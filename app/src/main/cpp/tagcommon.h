// Helpers shared by tagreader_jni.cpp (reading, the scan) and tagedit_jni.cpp
// (Edit Tags, spec 5.4). Strings cross as UTF-8 byte arrays (not jstring), so
// names with characters outside the BMP survive: JNI's NewStringUTF wants
// modified UTF-8.

#pragma once

#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <string>

#include <tfile.h>
#include <tpropertymap.h>
#include <mpegfile.h>
#include <id3v2tag.h>
#include <aifffile.h>
#include <wavfile.h>
#include <dsffile.h>
#include <dsdifffile.h>
#include <trueaudiofile.h>

namespace tagcommon {

inline std::string pathOf(JNIEnv *env, jbyteArray utf8) {
    const jsize n = env->GetArrayLength(utf8);
    std::string path(static_cast<size_t>(n), '\0');
    env->GetByteArrayRegion(utf8, 0, n, reinterpret_cast<jbyte *>(path.data()));
    return path;
}

inline jbyteArray bytes(JNIEnv *env, const char *data, size_t size) {
    jbyteArray array = env->NewByteArray(static_cast<jsize>(size));
    if (array != nullptr && size > 0)
        env->SetByteArrayRegion(array, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte *>(data));
    return array;
}

inline void put(JNIEnv *env, jobjectArray out, int slot, const std::string &value) {
    if (value.empty())
        return;
    jbyteArray b = bytes(env, value.data(), value.size());
    env->SetObjectArrayElement(out, slot, b);
    env->DeleteLocalRef(b);
}

inline void put(JNIEnv *env, jobjectArray out, int slot, long long value) {
    if (value != 0)
        put(env, out, slot, std::to_string(value));
}

inline std::string first(const TagLib::PropertyMap &map, const char *key) {
    auto it = map.find(key);
    if (it == map.end() || it->second.isEmpty())
        return {};
    return it->second.front().to8Bit(true);
}

// The ID3v2 tag of the formats that carry one, or null.
inline TagLib::ID3v2::Tag *id3v2Of(TagLib::File *file) {
    if (auto *f = dynamic_cast<TagLib::MPEG::File *>(file)) return f->hasID3v2Tag() ? f->ID3v2Tag() : nullptr;
    if (auto *f = dynamic_cast<TagLib::RIFF::AIFF::File *>(file)) return f->hasID3v2Tag() ? f->tag() : nullptr;
    if (auto *f = dynamic_cast<TagLib::RIFF::WAV::File *>(file)) return f->hasID3v2Tag() ? f->ID3v2Tag() : nullptr;
    if (auto *f = dynamic_cast<TagLib::DSF::File *>(file)) return f->tag();
    if (auto *f = dynamic_cast<TagLib::DSDIFF::File *>(file)) return f->hasID3v2Tag() ? f->ID3v2Tag() : nullptr;
    if (auto *f = dynamic_cast<TagLib::TrueAudio::File *>(file)) return f->hasID3v2Tag() ? f->ID3v2Tag() : nullptr;
    return nullptr;
}

inline bool canOpen(const std::string &path) {
    const int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0)
        return false;
    close(fd);
    return true;
}

}  // namespace tagcommon
