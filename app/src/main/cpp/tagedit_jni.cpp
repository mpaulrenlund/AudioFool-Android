// The JNI side of TagEditing.kt: Edit Tags (spec 5.4), the desktop's Edit Track
// Tags window. Reads the fields the library doesn't keep, and saves an edit with
// TagLib the way the desktop's TagWriter does with TagLibSharp: first against a
// stand-in for the file (DryRunStream), which says whether the save fits the room
// the tags already have; then either those bytes go into the file (Kotlin writes
// them), or the save is made on a copy beside the song (saveAs), which Kotlin
// checks and swaps in.
//
// Which field each format keeps a value in follows what TagLibSharp writes and
// reads, measured with the desktop's own TagLibSharp 2.3 on one file of each
// format (2026-11-11), so an edit made on the phone reads the same on the desktop:
//   Publisher  ID3v2 TPUB (TagLib's "LABEL"), Xiph ORGANIZATION, MP4 freeform
//              "----:com.apple.iTunes:publisher", ASF WM/Publisher. TagLibSharp
//              writes none in Ogg or APE tags; here Ogg takes ORGANIZATION too and
//              APE "PUBLISHER", which only the phone reads.
//   Conductor  ID3v2 TPE3, Xiph and APE CONDUCTOR, MP4 "cond", ASF WM/Conductor.
//   Composer, Genre  every value its own (ID3v2, Xiph, APE); MP4 one text, the
//              values joined by "; " as TagLibSharp writes them.
//   Comment    ID3v2 COMM, Xiph COMMENT (TagLibSharp neither reads nor writes
//              DESCRIPTION), MP4 ©cmt, APE Comment.

#include "tagcommon.h"

#include <algorithm>
#include <map>
#include <memory>
#include <optional>
#include <vector>
#include <cstring>

#include <fileref.h>
#include <tfilestream.h>
#include <audioproperties.h>
#include <id3v2header.h>
#include <flacfile.h>
#include <oggfile.h>
#include <mp4file.h>
#include <mp4tag.h>
#include <mp4item.h>
#include <asffile.h>
#include <apefile.h>
#include <mpcfile.h>
#include <wavpackfile.h>

namespace {

using tagcommon::pathOf;
using tagcommon::put;
using tagcommon::first;
using tagcommon::id3v2Of;
using tagcommon::canOpen;

// ---- the edit, as Kotlin passes it ----

// Slots of the edit array; TagEditing.kt names the same ones. Each is UTF-8:
// null leaves the field alone, empty clears it. Composer and Genre hold their
// values separated by '\0'.
enum EditSlot {
    E_TITLE, E_ARTIST, E_ALBUM_ARTIST, E_ALBUM, E_DATE,
    E_TRACK, E_TRACK_TOTAL, E_DISC, E_DISC_TOTAL,
    E_PUBLISHER, E_COMPOSER, E_CONDUCTOR, E_GENRE, E_COMMENT,
    EDIT_SLOTS
};

using Field = std::optional<std::string>;

struct Edit {
    Field slot[EDIT_SLOTS];
};

Edit editOf(JNIEnv *env, jobjectArray array) {
    Edit e;
    const jsize n = env->GetArrayLength(array);
    for (jsize i = 0; i < n && i < EDIT_SLOTS; i++) {
        auto b = static_cast<jbyteArray>(env->GetObjectArrayElement(array, i));
        if (b != nullptr) {
            e.slot[i] = pathOf(env, b);
            env->DeleteLocalRef(b);
        }
    }
    return e;
}

TagLib::String text(const std::string &utf8) { return {utf8, TagLib::String::UTF8}; }

std::string trimmed(const std::string &s) {
    const auto start = s.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return {};
    const auto end = s.find_last_not_of(" \t\r\n");
    return s.substr(start, end - start + 1);
}

TagLib::StringList valuesOf(const std::string &joined) {
    TagLib::StringList list;
    size_t start = 0;
    while (start <= joined.size()) {
        const size_t end = joined.find('\0', start);
        const std::string part = joined.substr(start, end == std::string::npos ? std::string::npos : end - start);
        if (!part.empty()) list.append(text(part));
        if (end == std::string::npos) break;
        start = end + 1;
    }
    return list;
}

std::string joinedWith(const TagLib::StringList &values, const char *separator) {
    std::string out;
    for (const auto &v : values) {
        if (!out.empty()) out += separator;
        out += v.to8Bit(true);
    }
    return out;
}

// ---- which family of tags a file keeps ----

enum class Family { Id3, Xiph, Mp4, Ape, Asf, Other };

Family familyOf(TagLib::File *file) {
    if (dynamic_cast<TagLib::FLAC::File *>(file) || dynamic_cast<TagLib::Ogg::File *>(file)) return Family::Xiph;
    if (dynamic_cast<TagLib::MP4::File *>(file)) return Family::Mp4;
    if (dynamic_cast<TagLib::ASF::File *>(file)) return Family::Asf;
    if (dynamic_cast<TagLib::WavPack::File *>(file) || dynamic_cast<TagLib::APE::File *>(file)
        || dynamic_cast<TagLib::MPC::File *>(file)) return Family::Ape;
    if (dynamic_cast<TagLib::MPEG::File *>(file) || dynamic_cast<TagLib::RIFF::WAV::File *>(file)
        || dynamic_cast<TagLib::RIFF::AIFF::File *>(file) || dynamic_cast<TagLib::DSF::File *>(file)
        || dynamic_cast<TagLib::DSDIFF::File *>(file) || dynamic_cast<TagLib::TrueAudio::File *>(file))
        return Family::Id3;
    return Family::Other;
}

const char *publisherKey(Family f) {
    switch (f) {
    case Family::Xiph: return "ORGANIZATION";
    case Family::Ape: return "PUBLISHER";
    default: return "LABEL";
    }
}

// MP4's two that TagLib's property names don't reach (TagLibSharp's own atoms).
const char *const kMp4Publisher = "----:com.apple.iTunes:publisher";
const char *const kMp4Conductor = "cond";

TagLib::MP4::Tag *mp4TagOf(TagLib::File *file) {
    auto *f = dynamic_cast<TagLib::MP4::File *>(file);
    return f ? f->tag() : nullptr;
}

std::string mp4Text(TagLib::MP4::Tag *tag, const char *name) {
    for (const auto &[key, item] : tag->itemMap()) {
        if (key.upper() == TagLib::String(name).upper()) {
            const auto list = item.toStringList();
            if (!list.isEmpty()) return list.front().to8Bit(true);
        }
    }
    return {};
}

void mp4Remove(TagLib::MP4::Tag *tag, const char *name) {
    std::vector<TagLib::String> gone;
    for (const auto &[key, item] : tag->itemMap())
        if (key.upper() == TagLib::String(name).upper()) gone.push_back(key);
    for (const auto &key : gone) tag->removeItem(key);
}

// ---- applying an edit (the desktop's TagWriter.WriteTrackTags) ----

void setText(TagLib::PropertyMap &p, const char *key, const std::string &value) {
    const std::string v = trimmed(value);
    if (v.empty()) p.erase(key);
    else p.replace(key, TagLib::StringList(text(v)));
}

void setList(TagLib::PropertyMap &p, const char *key, const TagLib::StringList &values) {
    if (values.isEmpty()) p.erase(key);
    else p.replace(key, values);
}

// "9/15", "9", "0/15" (a total without a number) or nothing.
std::string slashed(const std::string &number, const std::string &total) {
    if (number.empty() && total.empty()) return {};
    if (total.empty()) return number;
    return (number.empty() ? "0" : number) + "/" + total;
}

bool hasMonth(const std::string &date) { return date.size() > 4; }

/**
 * The ID3v2 version a save writes: the one the file has (TagLibSharp keeps a v2.3
 * a v2.3; TagLib alone writes v2.4), v2.4 for a new tag, and v2.4 for a date
 * naming a month: v2.3 splits a date into TYER and TDAT, which TagLib renders
 * month-first, so other players would read 2 October as 10 February (the
 * desktop's WriteDate). TagLib writes no v2.2, so a v2.2 tag becomes v2.3.
 */
int id3VersionFor(TagLib::File *file, const Edit &e) {
    int major = 4;
    if (TagLib::ID3v2::Tag *id3 = id3v2Of(file)) major = id3->header()->majorVersion();
    if (e.slot[E_DATE] && hasMonth(trimmed(*e.slot[E_DATE]))) major = 4;
    return major >= 4 ? 4 : 3;
}

/**
 * The edit, applied to [file]'s tags in memory. The fields the single-track
 * window always writes (title, album, date, the numbers) are set as given; the
 * artist and the album artist only where the box no longer reads the file's
 * first value, so a fixed title doesn't drop the "B" of "A; B" (the desktop's
 * session 69); the five details only when the user changed them.
 */
void apply(TagLib::File *file, const Edit &e, int id3Version) {
    const Family family = familyOf(file);
    TagLib::PropertyMap p = file->properties();

    if (e.slot[E_TITLE]) setText(p, "TITLE", *e.slot[E_TITLE]);
    if (e.slot[E_ARTIST]) {
        std::string shown = first(p, "ARTIST");
        if (shown.empty()) shown = first(p, "ALBUMARTIST");
        if (trimmed(*e.slot[E_ARTIST]) != trimmed(shown)) setText(p, "ARTIST", *e.slot[E_ARTIST]);
    }
    if (e.slot[E_ALBUM_ARTIST] && trimmed(*e.slot[E_ALBUM_ARTIST]) != trimmed(first(p, "ALBUMARTIST")))
        setText(p, "ALBUMARTIST", *e.slot[E_ALBUM_ARTIST]);
    if (e.slot[E_ALBUM]) setText(p, "ALBUM", *e.slot[E_ALBUM]);
    if (e.slot[E_DATE]) setText(p, "DATE", *e.slot[E_DATE]);

    // Numbers, written plainly (TagLibSharp pads track 1 as "01"; the desktop respells it).
    const std::string none;
    const auto &track = e.slot[E_TRACK] ? *e.slot[E_TRACK] : none;
    const auto &trackTotal = e.slot[E_TRACK_TOTAL] ? *e.slot[E_TRACK_TOTAL] : none;
    const auto &disc = e.slot[E_DISC] ? *e.slot[E_DISC] : none;
    const auto &discTotal = e.slot[E_DISC_TOTAL] ? *e.slot[E_DISC_TOTAL] : none;
    if (family == Family::Xiph) {
        // Xiph keeps each in a field of its own, as TagLibSharp writes them.
        setText(p, "TRACKNUMBER", track);
        setText(p, "TRACKTOTAL", trackTotal);
        p.erase("TOTALTRACKS");
        setText(p, "DISCNUMBER", disc);
        setText(p, "DISCTOTAL", discTotal);
        p.erase("TOTALDISCS");
    } else {
        setText(p, "TRACKNUMBER", slashed(track, trackTotal));
        setText(p, "DISCNUMBER", slashed(disc, discTotal));
    }

    TagLib::MP4::Tag *mp4 = family == Family::Mp4 ? mp4TagOf(file) : nullptr;
    // ID3v2.3 has no list of values in one frame (TagLib's would reach TagLibSharp as
    // the first value only, and a genre past the first not at all): the values go in
    // one text, joined as TagLibSharp joins them there, "/" for composers and ";"
    // for genres (measured on the emulator, 2026-11-11).
    const bool id3v23 = family == Family::Id3 && id3Version < 4;
    if (e.slot[E_PUBLISHER]) {
        if (mp4) p.erase("PUBLISHER");  // the freeform atom, set below
        else setText(p, publisherKey(family), *e.slot[E_PUBLISHER]);
    }
    if (e.slot[E_COMPOSER]) {
        const auto values = valuesOf(*e.slot[E_COMPOSER]);
        if (mp4) setText(p, "COMPOSER", joinedWith(values, "; "));
        else if (id3v23) setText(p, "COMPOSER", joinedWith(values, "/"));
        else setList(p, "COMPOSER", values);
    }
    if (e.slot[E_CONDUCTOR] && !mp4) setText(p, "CONDUCTOR", *e.slot[E_CONDUCTOR]);
    if (e.slot[E_GENRE]) {
        const auto values = valuesOf(*e.slot[E_GENRE]);
        if (mp4) setText(p, "GENRE", joinedWith(values, "; "));
        else if (id3v23) setText(p, "GENRE", joinedWith(values, ";"));
        else setList(p, "GENRE", values);
    }
    if (e.slot[E_COMMENT]) setText(p, "COMMENT", *e.slot[E_COMMENT]);

    file->setProperties(p);

    if (mp4) {
        if (e.slot[E_PUBLISHER]) {
            mp4Remove(mp4, kMp4Publisher);
            const std::string v = trimmed(*e.slot[E_PUBLISHER]);
            if (!v.empty()) mp4->setItem(kMp4Publisher, TagLib::StringList(text(v)));
        }
        if (e.slot[E_CONDUCTOR]) {
            mp4Remove(mp4, kMp4Conductor);
            const std::string v = trimmed(*e.slot[E_CONDUCTOR]);
            if (!v.empty()) mp4->setItem(kMp4Conductor, TagLib::StringList(text(v)));
        }
        // A numbered genre would stand beside the new one.
        if (e.slot[E_GENRE]) mp4->removeItem("gnre");
    }
}

/**
 * Saves [file] in the ID3v2 version [id3VersionFor] gives. Only the tags the file
 * has are written: no ID3v1 or APE tag is added to an MP3.
 */
bool save(TagLib::File *file, int id3Version) {
    using namespace TagLib;
    const ID3v2::Version version = id3Version >= 4 ? ID3v2::v4 : ID3v2::v3;

    if (auto *f = dynamic_cast<MPEG::File *>(file)) {
        int tags = MPEG::File::ID3v2;
        if (f->hasID3v1Tag()) tags |= MPEG::File::ID3v1;
        if (f->hasAPETag()) tags |= MPEG::File::APE;
        return f->save(tags, File::StripNone, version, File::DoNotDuplicate);
    }
    if (auto *f = dynamic_cast<RIFF::WAV::File *>(file)) return f->save(RIFF::WAV::File::AllTags, File::StripNone, version);
    if (auto *f = dynamic_cast<RIFF::AIFF::File *>(file)) return f->save(version);
    if (auto *f = dynamic_cast<DSF::File *>(file)) return f->save(version);
    if (auto *f = dynamic_cast<DSDIFF::File *>(file)) return f->save(DSDIFF::File::AllTags, File::StripNone, version);
    return file->save();
}

// ---- the stand-in (the desktop's DryRunStream) ----

// Thrown by the stand-in when the save would move the audio along the file.
struct MovesAudio {};

/**
 * A file as a save would leave it, without the file being touched: reads come
 * from the file, writes are kept here in 4 KB pages. A save that changes the
 * file's length, or writes more than [limit] bytes, moves the audio: the
 * stand-in stops it there (MovesAudio), and the real save is made on a copy.
 * The desktop's DryRunStream finds the same at the end; stopping at once spares
 * reading a whole song to shift it in memory. [shownName] is the song's own path,
 * by whose extension TagLib picks the format.
 */
class DryRunStream : public TagLib::IOStream {
public:
    DryRunStream(const std::string &path, std::string shownName, long long limit)
        : file_(path.c_str(), true), name_(std::move(shownName)), limit_(limit) {
        length_ = file_.isOpen() ? static_cast<long long>(file_.length()) : 0;
    }

    TagLib::FileName name() const override { return name_.c_str(); }
    bool readOnly() const override { return false; }
    bool isOpen() const override { return file_.isOpen(); }
    TagLib::offset_t tell() const override { return position_; }
    TagLib::offset_t length() override { return length_; }

    void seek(TagLib::offset_t offset, Position p = Beginning) override {
        long long to = offset;
        if (p == Current) to += position_;
        else if (p == End) to += length_;
        position_ = to < 0 ? 0 : to;
    }

    TagLib::ByteVector readBlock(size_t length) override {
        TagLib::ByteVector out;
        while (length > 0 && position_ < length_) {
            const long long page = position_ / kPage;
            const auto within = static_cast<size_t>(position_ % kPage);
            const size_t n = std::min<size_t>({length, kPage - within, static_cast<size_t>(length_ - position_)});
            auto kept = pages_.find(page);
            if (kept != pages_.end()) {
                out.append(TagLib::ByteVector(kept->second.data() + within, static_cast<unsigned int>(n)));
            } else {
                file_.seek(position_);
                out.append(file_.readBlock(n));
            }
            position_ += static_cast<long long>(n);
            length -= n;
        }
        return out;
    }

    void writeBlock(const TagLib::ByteVector &data) override {
        if (position_ + static_cast<long long>(data.size()) > length_) throw MovesAudio();
        size_t offset = 0;
        size_t count = data.size();
        while (count > 0) {
            const long long page = position_ / kPage;
            const auto within = static_cast<size_t>(position_ % kPage);
            const size_t n = std::min(count, kPage - within);
            auto kept = pages_.find(page);
            if (kept == pages_.end()) {
                if (static_cast<long long>(pages_.size() + 1) * kPage > limit_) throw MovesAudio();
                std::vector<char> fresh(kPage, 0);
                file_.seek(page * kPage);
                const TagLib::ByteVector had = file_.readBlock(kPage);
                std::memcpy(fresh.data(), had.data(), had.size());
                kept = pages_.emplace(page, std::move(fresh)).first;
            }
            std::memcpy(kept->second.data() + within, data.data() + offset, n);
            position_ += static_cast<long long>(n);
            offset += n;
            count -= n;
        }
    }

    void insert(const TagLib::ByteVector &data, TagLib::offset_t start, size_t replace) override {
        if (data.size() != replace) throw MovesAudio();
        seek(start);
        writeBlock(data);
    }

    void removeBlock(TagLib::offset_t, size_t length) override {
        if (length > 0) throw MovesAudio();
    }

    void truncate(TagLib::offset_t length) override {
        if (length != length_) throw MovesAudio();
    }

    /** The pages the save wrote, in file order: their place and their bytes (the last cut at the file's end). */
    std::vector<std::pair<long long, std::string>> changes() const {
        std::vector<std::pair<long long, std::string>> out;
        for (const auto &[page, bytes] : pages_) {
            const long long at = page * kPage;
            if (at >= length_) continue;
            const auto n = static_cast<size_t>(std::min<long long>(kPage, length_ - at));
            out.emplace_back(at, std::string(bytes.data(), n));
        }
        return out;
    }

private:
    static constexpr size_t kPage = 4096;
    mutable TagLib::FileStream file_;
    std::string name_;
    long long limit_;
    long long length_ = 0;
    long long position_ = 0;
    std::map<long long, std::vector<char>> pages_;
};

/** A file opened under another's name: a working copy ends in ".part", and TagLib picks a format by the song's extension. */
class NamedFileStream : public TagLib::FileStream {
public:
    NamedFileStream(const std::string &path, std::string shownName, bool readOnly)
        : TagLib::FileStream(path.c_str(), readOnly), name_(std::move(shownName)) {}
    TagLib::FileName name() const override { return name_.c_str(); }

private:
    std::string name_;
};

// Slots of what rehearse() returns: the outcome, a message, then each change's
// place (decimal text) and bytes, in pairs.
const char *const kInPlace = "inplace";
const char *const kMoves = "moves";
const char *const kFailed = "failed";

jobjectArray outcome(JNIEnv *env, const char *status, const std::string &message,
                     const std::vector<std::pair<long long, std::string>> &changes = {}) {
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray out = env->NewObjectArray(static_cast<jsize>(2 + changes.size() * 2), byteArrayClass, nullptr);
    put(env, out, 0, std::string(status));
    put(env, out, 1, message);
    jsize i = 2;
    for (const auto &[at, data] : changes) {
        put(env, out, i++, std::to_string(at));
        jbyteArray b = tagcommon::bytes(env, data.data(), data.size());
        env->SetObjectArrayElement(out, i++, b);
        env->DeleteLocalRef(b);
    }
    return out;
}

// What TagLib couldn't do, in the desktop's words (its TagWriter.Describe).
const char *const kUnsupported = "unsupported file format";
const char *const kCorrupt = "the file appears to be corrupt";
const char *const kNotSaved = "TagLib couldn't save the tags";

}  // namespace

// The details a scan doesn't keep, as the file holds them: [status ("read",
// "unparsed", "unopened"), publisher, composer values ('\0' between), conductor,
// genre values, comment, date, then the track number, track total, disc number
// and disc total as written].
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_mpaulrenlund_audiofool_library_TagEditing_details(JNIEnv *env, jobject, jbyteArray pathUtf8) {
    enum { STATUS, PUBLISHER, COMPOSER, CONDUCTOR, GENRE, COMMENT, DATE, TRACK, TRACK_TOTAL, DISC, DISC_TOTAL, SLOTS };
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray out = env->NewObjectArray(SLOTS, byteArrayClass, nullptr);
    const std::string path = pathOf(env, pathUtf8);
    if (!canOpen(path)) {
        put(env, out, STATUS, std::string("unopened"));
        return out;
    }
    TagLib::FileRef ref(path.c_str(), false);
    TagLib::File *file = ref.file();
    if (ref.isNull() || file == nullptr || !file->isValid()) {
        put(env, out, STATUS, std::string("unparsed"));
        return out;
    }
    put(env, out, STATUS, std::string("read"));

    const Family family = familyOf(file);
    const TagLib::PropertyMap p = file->properties();
    TagLib::ID3v2::Tag *id3 = id3v2Of(file);
    const bool oldId3 = id3 != nullptr && !id3->isEmpty() && id3->header()->majorVersion() < 4;
    // Every value, '\0' between (joinedWith takes a C string, which can't hold one);
    // each also split at [within], where a format keeps several in one text.
    auto values = [&](const char *key, char within) {
        std::string out;
        auto it = p.find(key);
        if (it == p.end()) return out;
        for (const auto &v : it->second) {
            std::string text = v.to8Bit(true);
            if (within != '\0') std::replace(text.begin(), text.end(), within, '\0');
            if (!out.empty()) out.push_back('\0');
            out += text;
        }
        return out;
    };

    if (TagLib::MP4::Tag *mp4 = family == Family::Mp4 ? mp4TagOf(file) : nullptr) {
        put(env, out, PUBLISHER, mp4Text(mp4, kMp4Publisher));
        put(env, out, CONDUCTOR, mp4Text(mp4, kMp4Conductor));
    } else {
        put(env, out, PUBLISHER, first(p, publisherKey(family)));
        put(env, out, CONDUCTOR, first(p, "CONDUCTOR"));
    }
    // An ID3v2.3 tag keeps several composers as "A/B" and genres as "A;B" (TagLibSharp's
    // way, which it reads back as two each; the edit writes them so).
    put(env, out, COMPOSER, values("COMPOSER", oldId3 ? '/' : '\0'));
    put(env, out, GENRE, values("GENRE", oldId3 ? ';' : '\0'));
    put(env, out, COMMENT, first(p, "COMMENT"));

    // As the reader's DATE: only ID3v2.4 stores a date whole (v2.3's TYER and TDAT
    // merge with day and month swapped), so a v2.3 file's date isn't used.
    if (!oldId3) put(env, out, DATE, first(p, "DATE"));

    put(env, out, TRACK, first(p, "TRACKNUMBER"));
    std::string trackTotal = first(p, "TRACKTOTAL");
    if (trackTotal.empty()) trackTotal = first(p, "TOTALTRACKS");
    put(env, out, TRACK_TOTAL, trackTotal);
    put(env, out, DISC, first(p, "DISCNUMBER"));
    std::string discTotal = first(p, "DISCTOTAL");
    if (discTotal.empty()) discTotal = first(p, "TOTALDISCS");
    put(env, out, DISC_TOTAL, discTotal);
    return out;
}

// The save made against a stand-in for the file at [path] (only read): "inplace"
// with the bytes to write, "moves" (the save is to be made on a copy), or
// "failed" with why. [limit] is the most an in-place save may write.
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_mpaulrenlund_audiofool_library_TagEditing_rehearse(JNIEnv *env, jobject, jbyteArray pathUtf8, jobjectArray editSlots, jlong limit) {
    const std::string path = pathOf(env, pathUtf8);
    const Edit edit = editOf(env, editSlots);
    try {
        DryRunStream stand(path, path, limit);
        if (!stand.isOpen()) return outcome(env, kFailed, "the file couldn't be opened");
        bool saved;
        {
            TagLib::FileRef ref(&stand, true, TagLib::AudioProperties::Average);
            TagLib::File *file = ref.file();
            if (ref.isNull() || file == nullptr) return outcome(env, kFailed, kUnsupported);
            if (!file->isValid()) return outcome(env, kFailed, kCorrupt);
            const int id3Version = id3VersionFor(file, edit);
            apply(file, edit, id3Version);
            saved = save(file, id3Version);
        }
        if (!saved) return outcome(env, kFailed, kNotSaved);
        return outcome(env, kInPlace, "", stand.changes());
    } catch (const MovesAudio &) {
        return outcome(env, kMoves, "");
    } catch (const std::exception &ex) {
        return outcome(env, kFailed, std::string("Unexpected error: ") + ex.what());
    }
}

// The save made for real on [copyPath], a working copy of [songPath] (whose
// extension tells TagLib the format). Null when saved, else why not.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_mpaulrenlund_audiofool_library_TagEditing_saveAs(JNIEnv *env, jobject, jbyteArray copyUtf8, jbyteArray songUtf8, jobjectArray editSlots) {
    const std::string copy = pathOf(env, copyUtf8);
    const std::string song = pathOf(env, songUtf8);
    const Edit edit = editOf(env, editSlots);
    auto fail = [&](const std::string &why) { return tagcommon::bytes(env, why.data(), why.size()); };
    try {
        NamedFileStream stream(copy, song, false);
        if (!stream.isOpen() || stream.readOnly()) return fail("the working copy couldn't be opened for writing");
        bool saved;
        {
            TagLib::FileRef ref(&stream, true, TagLib::AudioProperties::Average);
            TagLib::File *file = ref.file();
            if (ref.isNull() || file == nullptr) return fail(kUnsupported);
            if (!file->isValid()) return fail(kCorrupt);
            const int id3Version = id3VersionFor(file, edit);
            apply(file, edit, id3Version);
            saved = save(file, id3Version);
        }
        return saved ? nullptr : fail(kNotSaved);
    } catch (const std::exception &ex) {
        return fail(std::string("Unexpected error: ") + ex.what());
    }
}

// [length in ms, sample rate, channels] as TagLib reads [path] (named [shownName]
// for its format), for the check that a saved copy plays the same audio; null
// when it can't be read.
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mpaulrenlund_audiofool_library_TagEditing_audio(JNIEnv *env, jobject, jbyteArray pathUtf8, jbyteArray shownUtf8) {
    const std::string path = pathOf(env, pathUtf8);
    const std::string shown = pathOf(env, shownUtf8);
    try {
        NamedFileStream stream(path, shown, true);
        if (!stream.isOpen()) return nullptr;
        TagLib::FileRef ref(&stream, true, TagLib::AudioProperties::Average);
        if (ref.isNull() || ref.file() == nullptr || !ref.file()->isValid()) return nullptr;
        TagLib::AudioProperties *props = ref.audioProperties();
        if (props == nullptr) return nullptr;
        const jlong values[3] = {props->lengthInMilliseconds(), props->sampleRate(), props->channels()};
        jlongArray out = env->NewLongArray(3);
        if (out != nullptr) env->SetLongArrayRegion(out, 0, 3, values);
        return out;
    } catch (const std::exception &) {
        return nullptr;
    }
}
