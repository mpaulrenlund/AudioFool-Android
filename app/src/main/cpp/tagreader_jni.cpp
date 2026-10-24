// The JNI side of TagLibNative.kt: reads one file's tags and audio properties
// with TagLib 2.x, the C++ library the desktop's TagLibSharp is ported from.
// Strings cross as UTF-8 byte arrays (not jstring), so names with characters
// outside the BMP survive: JNI's NewStringUTF wants modified UTF-8.

#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <string>

#include <fileref.h>
#include <tfile.h>
#include <tpropertymap.h>
#include <tvariant.h>
#include <audioproperties.h>
#include <mpegfile.h>
#include <id3v2tag.h>
#include <id3v2header.h>
#include <aifffile.h>
#include <wavfile.h>
#include <dsffile.h>
#include <dsdifffile.h>
#include <trueaudiofile.h>
#include <flacproperties.h>
#include <mp4properties.h>
#include <dsfproperties.h>
#include <dsdiffproperties.h>
#include <apeproperties.h>
#include <wavpackproperties.h>
#include <wavproperties.h>
#include <aiffproperties.h>
#include <trueaudioproperties.h>
#include <shortenproperties.h>

namespace {

// Slots of the array read() returns; TagLibNative.kt names the same ones.
enum Slot {
    STATUS, TITLE, ARTIST, ALBUM_ARTIST, ALBUM,
    TRACK, TRACK_TOTAL, DISC, DISC_TOTAL, YEAR, DATE,
    LENGTH_MS, SAMPLE_RATE, BITRATE, BITS_PER_SAMPLE, IS_ALAC,
    SLOT_COUNT
};

// STATUS values.
const char *const kRead = "read";
const char *const kUnparsed = "unparsed";   // opens, but TagLib can't make sense of it
const char *const kUnopened = "unopened";   // can't be opened: card gone, or no access

std::string pathOf(JNIEnv *env, jbyteArray utf8) {
    const jsize n = env->GetArrayLength(utf8);
    std::string path(static_cast<size_t>(n), '\0');
    env->GetByteArrayRegion(utf8, 0, n, reinterpret_cast<jbyte *>(path.data()));
    return path;
}

jbyteArray bytes(JNIEnv *env, const char *data, size_t size) {
    jbyteArray array = env->NewByteArray(static_cast<jsize>(size));
    if (array != nullptr && size > 0)
        env->SetByteArrayRegion(array, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte *>(data));
    return array;
}

void put(JNIEnv *env, jobjectArray out, int slot, const std::string &value) {
    if (value.empty())
        return;
    jbyteArray b = bytes(env, value.data(), value.size());
    env->SetObjectArrayElement(out, slot, b);
    env->DeleteLocalRef(b);
}

void put(JNIEnv *env, jobjectArray out, int slot, long long value) {
    if (value != 0)
        put(env, out, slot, std::to_string(value));
}

std::string first(const TagLib::PropertyMap &map, const char *key) {
    auto it = map.find(key);
    if (it == map.end() || it->second.isEmpty())
        return {};
    return it->second.front().to8Bit(true);
}

// The ID3v2 tag of the formats that carry one, or null.
TagLib::ID3v2::Tag *id3v2Of(TagLib::File *file) {
    if (auto *f = dynamic_cast<TagLib::MPEG::File *>(file)) return f->hasID3v2Tag() ? f->ID3v2Tag() : nullptr;
    if (auto *f = dynamic_cast<TagLib::RIFF::AIFF::File *>(file)) return f->hasID3v2Tag() ? f->tag() : nullptr;
    if (auto *f = dynamic_cast<TagLib::RIFF::WAV::File *>(file)) return f->hasID3v2Tag() ? f->ID3v2Tag() : nullptr;
    if (auto *f = dynamic_cast<TagLib::DSF::File *>(file)) return f->tag();
    if (auto *f = dynamic_cast<TagLib::DSDIFF::File *>(file)) return f->hasID3v2Tag() ? f->ID3v2Tag() : nullptr;
    if (auto *f = dynamic_cast<TagLib::TrueAudio::File *>(file)) return f->hasID3v2Tag() ? f->ID3v2Tag() : nullptr;
    return nullptr;
}

int bitsPerSample(TagLib::AudioProperties *p) {
    if (auto *x = dynamic_cast<TagLib::FLAC::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<TagLib::MP4::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<TagLib::DSF::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<TagLib::DSDIFF::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<TagLib::APE::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<TagLib::WavPack::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<TagLib::RIFF::WAV::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<TagLib::RIFF::AIFF::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<TagLib::TrueAudio::Properties *>(p)) return x->bitsPerSample();
    if (auto *x = dynamic_cast<TagLib::Shorten::Properties *>(p)) return x->bitsPerSample();
    return 0;
}

bool canOpen(const std::string &path) {
    const int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0)
        return false;
    close(fd);
    return true;
}

}  // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_mpaulrenlund_audiofool_library_TagLibNative_read(JNIEnv *env, jobject, jbyteArray pathUtf8) {
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray out = env->NewObjectArray(SLOT_COUNT, byteArrayClass, nullptr);
    const std::string path = pathOf(env, pathUtf8);

    if (!canOpen(path)) {
        put(env, out, STATUS, kUnopened);
        return out;
    }

    TagLib::FileRef ref(path.c_str(), true, TagLib::AudioProperties::Average);
    TagLib::File *file = ref.file();
    if (ref.isNull() || file == nullptr || !file->isValid()) {
        put(env, out, STATUS, kUnparsed);
        return out;
    }

    put(env, out, STATUS, kRead);
    const TagLib::PropertyMap map = file->properties();

    put(env, out, TITLE, first(map, "TITLE"));
    std::string artist = first(map, "ARTIST");
    if (artist.empty())
        artist = first(map, "ALBUMARTIST");
    put(env, out, ARTIST, artist);
    put(env, out, ALBUM_ARTIST, first(map, "ALBUMARTIST"));
    put(env, out, ALBUM, first(map, "ALBUM"));

    // "3/12" or "3"; Kotlin splits it and falls back to the *TOTAL fields.
    put(env, out, TRACK, first(map, "TRACKNUMBER"));
    std::string trackTotal = first(map, "TRACKTOTAL");
    if (trackTotal.empty()) trackTotal = first(map, "TOTALTRACKS");
    put(env, out, TRACK_TOTAL, trackTotal);
    put(env, out, DISC, first(map, "DISCNUMBER"));
    std::string discTotal = first(map, "DISCTOTAL");
    if (discTotal.empty()) discTotal = first(map, "TOTALDISCS");
    put(env, out, DISC_TOTAL, discTotal);

    if (TagLib::Tag *tag = file->tag())
        put(env, out, YEAR, static_cast<long long>(tag->year()));

    // The desktop's TagReader.ReadDate: only ID3v2.4 stores a date whole; from
    // v2.3, TYER and TDAT merge with day and month swapped, so its date is not used.
    TagLib::ID3v2::Tag *id3 = id3v2Of(file);
    const bool oldId3 = id3 != nullptr && !id3->isEmpty() && id3->header()->majorVersion() < 4;
    if (!oldId3)
        put(env, out, DATE, first(map, "DATE"));

    if (TagLib::AudioProperties *props = file->audioProperties()) {
        put(env, out, LENGTH_MS, static_cast<long long>(props->lengthInMilliseconds()));
        put(env, out, SAMPLE_RATE, static_cast<long long>(props->sampleRate()));
        put(env, out, BITRATE, static_cast<long long>(props->bitrate()));
        put(env, out, BITS_PER_SAMPLE, static_cast<long long>(bitsPerSample(props)));
        if (auto *mp4 = dynamic_cast<TagLib::MP4::Properties *>(props))
            if (mp4->codec() == TagLib::MP4::Properties::ALAC)
                put(env, out, IS_ALAC, "1");
    }

    return out;
}

// Every embedded picture as [type, data, type, data, ...], type being TagLib's
// picture type name ("Front Cover", ...). Null when the file can't be read.
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_mpaulrenlund_audiofool_library_TagLibNative_pictures(JNIEnv *env, jobject, jbyteArray pathUtf8) {
    const std::string path = pathOf(env, pathUtf8);
    if (!canOpen(path))
        return nullptr;

    TagLib::FileRef ref(path.c_str(), false);
    if (ref.isNull() || ref.file() == nullptr || !ref.file()->isValid())
        return nullptr;

    const TagLib::List<TagLib::VariantMap> pictures = ref.file()->complexProperties("PICTURE");
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray out = env->NewObjectArray(static_cast<jsize>(pictures.size() * 2), byteArrayClass, nullptr);

    jsize i = 0;
    for (const auto &picture : pictures) {
        const std::string type = picture.value("pictureType").toString().to8Bit(true);
        const TagLib::ByteVector data = picture.value("data").toByteVector();
        jbyteArray t = bytes(env, type.data(), type.size());
        jbyteArray d = bytes(env, data.data(), data.size());
        env->SetObjectArrayElement(out, i++, t);
        env->SetObjectArrayElement(out, i++, d);
        env->DeleteLocalRef(t);
        env->DeleteLocalRef(d);
    }
    return out;
}
