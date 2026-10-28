// AudioFool's own USB Audio driver (0.4.0, true Bit-Perfect on the S24; design/usb-bit-perfect.md).
//
// The S24 offers no bit-perfect mixer and no 44.1 kHz for a USB DAC, so with Bit-Perfect on the
// app takes the DAC from Android's driver and plays to it itself, through libusb on the file
// descriptor Android's UsbManager gave it: the track's own rate and samples, unchanged.
//
// One stream at a time: isochronous OUT transfers to the DAC's data endpoint, a few in flight,
// each packet sized by the DAC's own clock as its feedback endpoint reports it (an asynchronous
// DAC). The output stage writes samples, already in the DAC's slot format, into a ring; the
// libusb event thread takes them out as it refills each transfer. Paused, the stream goes on
// with silence, so the DAC keeps its clock. The class requests (the clock's rate, the volume)
// are made from Kotlin on the same descriptor.
//
// The feedback and packet sums are Linux's (sound/usb/endpoint.c: snd_usb_handle_sync_urb and
// next_packet_size), the same as core's UsbFeedback and PacketSizer, whose tests cover them.

#include <jni.h>
#include <android/log.h>
#include <libusb.h>

#include <algorithm>
#include <atomic>
#include <cerrno>
#include <climits>
#include <cmath>
#include <condition_variable>
#include <cstring>
#include <mutex>
#include <thread>
#include <vector>

#include <sys/resource.h>
#include <time.h>
#include <unistd.h>

#define TAG "AudioFoolUsb"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace {

// 8 transfers of 8 ms: 64 ms in flight. 0.4.0 had 16 ms, and the music stopped for a moment
// when the app went to the background (the S24): Android runs it on slower cores then, and
// the refills came too late.
constexpr int DATA_TRANSFERS = 8;
constexpr int FEEDBACK_TRANSFERS = 2;
// Errors in a row on the data endpoint before the DAC counts as gone.
constexpr int ERRORS_IN_A_ROW = 50;

struct Stream;

struct DataSlot {
    libusb_transfer* transfer = nullptr;
    Stream* stream = nullptr;
    int audioFrames = 0;   // frames from the ring in this transfer, counted once it is played
    uint32_t generation = 0;
};

struct Stream {
    libusb_device_handle* handle = nullptr;
    int interfaceNumber = 0;
    int bytesPerFrame = 4;
    int slotBytes = 2;
    int maxFrames = 0;
    int dataInterval = 0;
    int packetsPerTransfer = 8;

    // The ring, the play state and the fade: the writer's thread and the event thread.
    std::mutex lock;
    std::vector<uint8_t> ring;
    size_t readAt = 0, writeAt = 0, filled = 0;
    bool playing = false;
    bool fed = false;            // written to since the last flush: running dry is an underrun
    int fadeTotal = 0, fadeLeft = 0;
    bool fadedOut = false;
    uint32_t generation = 0;     // a flush drops what was in flight from the count
    int64_t phase = 0;

    // Linux's feedback state: Q16.16 frames per (micro)frame.
    int nominal = 0;
    std::atomic<int> rate{0};
    int shift = INT_MIN;

    std::atomic<int64_t> head{0};
    std::atomic<int> underruns{0};
    std::atomic<int> feedbackValues{0};
    std::atomic<int> errors{0};
    std::atomic<bool> lost{false};
    std::atomic<bool> stopping{false};
    std::atomic<int> inFlight{0};
    std::mutex doneLock;
    std::condition_variable done;
    int errorsInARow = 0;

    // How late the refills come (0.4.2): the longest gap between two, and how many came after
    // the DAC had played all that was in flight (heard as a gap).
    int queuedMs = 0, transferMs = 0;
    int64_t lastCallbackMs = 0;
    std::atomic<int> longestGapMs{0};
    std::atomic<int> lateRefills{0};

    DataSlot data[DATA_TRANSFERS];
    libusb_transfer* feedback[FEEDBACK_TRANSFERS] = {};
};

struct Device {
    libusb_context* context = nullptr;
    libusb_device_handle* handle = nullptr;
    std::thread events;
    std::atomic<bool> quit{false};
    // The nice value the event thread got (0.4.2): -19 asked; anything else says Android refused.
    std::atomic<int> eventNice{99};
    Stream* stream = nullptr;
};

int nominalRate(int sampleRate, bool highSpeed) {
    // Linux's get_usb_high_speed_rate and get_usb_full_speed_rate.
    int64_t r = sampleRate;
    return static_cast<int>(highSpeed ? ((r << 10) + 62) / 125 : ((r << 13) + 62) / 125);
}

void takeFeedback(Stream* s, uint32_t value) {
    if (value == 0) return;
    int64_t f = value;
    int n = s->nominal;
    if (s->shift == INT_MIN) {
        int shift = 0;
        while (f < n - n / 4) { f <<= 1; shift++; }
        while (f > n + n / 2) { f >>= 1; shift--; }
        s->shift = shift;
    } else if (s->shift >= 0) {
        f <<= s->shift;
    } else {
        f >>= -s->shift;
    }
    if (f >= n - n / 8 && f <= n + n / 2) {
        s->rate.store(static_cast<int>(f));
        s->feedbackValues++;
    } else {
        s->shift = INT_MIN;
    }
}

int nextPacketFrames(Stream* s) {
    s->phase = (s->phase & 0xFFFF) + (static_cast<int64_t>(s->rate.load()) << s->dataInterval);
    int frames = static_cast<int>(s->phase >> 16);
    return frames < s->maxFrames ? frames : s->maxFrames;
}

// A raised-cosine gain falling from 1 to 0 over the fade, as the desktop's seek fade does.
void fadeSamples(Stream* s, uint8_t* p, int frames) {
    int channels = s->bytesPerFrame / s->slotBytes;
    for (int i = 0; i < frames; i++) {
        double gain = s->fadeLeft > 0 ? 0.5 * (1.0 + std::cos(M_PI * (s->fadeTotal - s->fadeLeft) / s->fadeTotal)) : 0.0;
        if (s->fadeLeft > 0) s->fadeLeft--;
        for (int c = 0; c < channels; c++, p += s->slotBytes) {
            switch (s->slotBytes) {
                case 2: {
                    int16_t v; memcpy(&v, p, 2);
                    v = static_cast<int16_t>(std::lround(v * gain));
                    memcpy(p, &v, 2);
                    break;
                }
                case 3: {
                    int32_t v = p[0] | (p[1] << 8) | (static_cast<int8_t>(p[2]) << 16);
                    v = static_cast<int32_t>(std::lround(v * gain));
                    p[0] = v & 0xFF; p[1] = (v >> 8) & 0xFF; p[2] = (v >> 16) & 0xFF;
                    break;
                }
                default: {
                    int32_t v; memcpy(&v, p, 4);
                    v = static_cast<int32_t>(std::llround(static_cast<double>(v) * gain));
                    memcpy(p, &v, 4);
                    break;
                }
            }
        }
    }
}

// Fills one transfer's packets: samples from the ring while playing, silence for the rest.
void fill(DataSlot* slot) {
    Stream* s = slot->stream;
    libusb_transfer* t = slot->transfer;
    uint8_t* out = t->buffer;
    int total = 0, audio = 0;
    bool ranDry = false;
    std::lock_guard<std::mutex> g(s->lock);
    for (int i = 0; i < t->num_iso_packets; i++) {
        int frames = nextPacketFrames(s);
        int bytes = frames * s->bytesPerFrame;
        int take = 0;
        if (s->playing && !s->fadedOut) {
            int available = static_cast<int>(s->filled / s->bytesPerFrame);
            take = frames < available ? frames : available;
            if (take < frames && s->fed) ranDry = true;
            int takeBytes = take * s->bytesPerFrame;
            size_t first = std::min(static_cast<size_t>(takeBytes), s->ring.size() - s->readAt);
            memcpy(out + total, s->ring.data() + s->readAt, first);
            memcpy(out + total + first, s->ring.data(), takeBytes - first);
            s->readAt = (s->readAt + takeBytes) % s->ring.size();
            s->filled -= takeBytes;
            if (s->fadeTotal > 0 && take > 0) {
                fadeSamples(s, out + total, take);
                if (s->fadeLeft == 0) s->fadedOut = true;
            }
        }
        memset(out + total + take * s->bytesPerFrame, 0, bytes - take * s->bytesPerFrame);
        t->iso_packet_desc[i].length = bytes;
        total += bytes;
        audio += take;
    }
    t->length = total;
    slot->audioFrames = audio;
    slot->generation = s->generation;
    if (ranDry) {
        s->underruns++;
        s->fed = false;   // once per running dry, not every transfer until the next write
    }
}

void finished(Stream* s) {
    if (--s->inFlight == 0) {
        std::lock_guard<std::mutex> g(s->doneLock);
        s->done.notify_all();
    }
}

int64_t nowMs() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000 + ts.tv_nsec / 1000000;
}

void LIBUSB_CALL onData(libusb_transfer* t) {
    auto* slot = static_cast<DataSlot*>(t->user_data);
    Stream* s = slot->stream;
    int64_t now = nowMs();
    if (s->lastCallbackMs != 0) {
        int gap = static_cast<int>(now - s->lastCallbackMs);
        if (gap > s->longestGapMs) s->longestGapMs = gap;
        // All but this transfer had played out before it came back: the DAC went short.
        if (gap > s->queuedMs - s->transferMs) s->lateRefills++;
    }
    s->lastCallbackMs = now;
    if (t->status == LIBUSB_TRANSFER_COMPLETED) {
        s->errorsInARow = 0;
        if (slot->generation == s->generation) s->head += slot->audioFrames;
    } else if (t->status == LIBUSB_TRANSFER_NO_DEVICE) {
        s->lost = true;
    } else if (t->status != LIBUSB_TRANSFER_CANCELLED) {
        s->errors++;
        if (++s->errorsInARow >= ERRORS_IN_A_ROW) s->lost = true;
    }
    if (s->stopping || s->lost || t->status == LIBUSB_TRANSFER_CANCELLED) {
        finished(s);
        return;
    }
    fill(slot);
    int r = libusb_submit_transfer(t);
    if (r != 0) {
        LOGW("data resubmit: %s", libusb_error_name(r));
        if (r == LIBUSB_ERROR_NO_DEVICE) s->lost = true;
        finished(s);
    }
}

void LIBUSB_CALL onFeedback(libusb_transfer* t) {
    auto* s = static_cast<Stream*>(t->user_data);
    if (t->status == LIBUSB_TRANSFER_COMPLETED && t->iso_packet_desc[0].status == LIBUSB_TRANSFER_COMPLETED) {
        int n = static_cast<int>(t->iso_packet_desc[0].actual_length);
        if (n >= 3) {
            uint32_t v = 0;
            for (int i = 0; i < n && i < 4; i++) v |= static_cast<uint32_t>(t->buffer[i]) << (8 * i);
            takeFeedback(s, v & 0x0FFFFFFF);
        }
    } else if (t->status == LIBUSB_TRANSFER_NO_DEVICE) {
        s->lost = true;
    }
    if (s->stopping || s->lost || t->status == LIBUSB_TRANSFER_CANCELLED) {
        finished(s);
        return;
    }
    if (libusb_submit_transfer(t) != 0) finished(s);
}

void runEvents(Device* d) {
    // The audio thread's priority: the transfers must be refilled every couple of milliseconds.
    if (setpriority(PRIO_PROCESS, gettid(), -19) != 0) LOGW("event thread priority: errno %d", errno);
    errno = 0;
    d->eventNice = getpriority(PRIO_PROCESS, gettid());
    while (!d->quit) {
        timeval tv{0, 100000};
        libusb_handle_events_timeout_completed(d->context, &tv, nullptr);
    }
}

void stopStream(Device* d) {
    Stream* s = d->stream;
    if (s == nullptr) return;
    s->stopping = true;
    // A callback already past its check may submit once more, so cancel until none is left.
    for (int tries = 0; tries < 40 && s->inFlight.load() > 0; tries++) {
        for (auto& slot : s->data) if (slot.transfer) libusb_cancel_transfer(slot.transfer);
        for (auto* t : s->feedback) if (t) libusb_cancel_transfer(t);
        std::unique_lock<std::mutex> g(s->doneLock);
        s->done.wait_for(g, std::chrono::milliseconds(50), [s] { return s->inFlight.load() <= 0; });
    }
    if (s->inFlight.load() > 0) {
        // Never free a transfer libusb may still call back on; a small leak instead.
        LOGW("stop: %d transfers still in flight", s->inFlight.load());
        d->stream = nullptr;
        return;
    }
    for (auto& slot : s->data) if (slot.transfer) { delete[] slot.transfer->buffer; libusb_free_transfer(slot.transfer); }
    for (auto* t : s->feedback) if (t) { delete[] t->buffer; libusb_free_transfer(t); }
    if (!s->lost) libusb_set_interface_alt_setting(d->handle, s->interfaceNumber, 0);
    delete s;
    d->stream = nullptr;
}

Device* device(jlong h) { return reinterpret_cast<Device*>(h); }

}  // namespace

extern "C" {

/** Opens the DAC from Android's file descriptor; 0 if libusb can't. */
JNIEXPORT jlong JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeOpen(JNIEnv*, jclass, jint fd) {
    auto* d = new Device();
    // No device discovery: an app may not list /dev/bus/usb; it has only this descriptor.
    libusb_init_option options[] = {{LIBUSB_OPTION_NO_DEVICE_DISCOVERY, {0}}};
    int r = libusb_init_context(&d->context, options, 1);
    if (r == 0) r = libusb_wrap_sys_device(d->context, static_cast<intptr_t>(fd), &d->handle);
    if (r != 0) {
        LOGW("open: %s", libusb_error_name(r));
        if (d->context) libusb_exit(d->context);
        delete d;
        return 0;
    }
    d->events = std::thread(runEvents, d);
    return reinterpret_cast<jlong>(d);
}

/** True when the DAC runs at high speed (USB 2.0: microframes, 8,000 a second). */
JNIEXPORT jboolean JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeHighSpeed(JNIEnv*, jclass, jlong h) {
    int speed = libusb_get_device_speed(libusb_get_device(device(h)->handle));
    return speed >= LIBUSB_SPEED_HIGH;
}

/** Takes [interfaces] from Android's driver and claims them. 0, or libusb's error. */
JNIEXPORT jint JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeClaim(JNIEnv* env, jclass, jlong h, jintArray interfaces) {
    Device* d = device(h);
    jsize n = env->GetArrayLength(interfaces);
    std::vector<jint> list(n);
    env->GetIntArrayRegion(interfaces, 0, n, list.data());
    for (jint i : list) {
        int r = libusb_detach_kernel_driver(d->handle, i);
        if (r != 0 && r != LIBUSB_ERROR_NOT_FOUND) LOGW("detach %d: %s", i, libusb_error_name(r));
        r = libusb_claim_interface(d->handle, i);
        if (r != 0) {
            LOGW("claim %d: %s", i, libusb_error_name(r));
            return r;
        }
    }
    return 0;
}

/** Sets [interfaceNumber]'s alternate setting (0: no bandwidth). 0, or libusb's error. */
JNIEXPORT jint JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeSetAlternate(JNIEnv*, jclass, jlong h, jint interfaceNumber, jint alternate) {
    return libusb_set_interface_alt_setting(device(h)->handle, interfaceNumber, alternate);
}

/**
 * Starts streaming on the alternate setting already set: [bufferBytes] of ring for the
 * samples, paused until told to play. 0, or libusb's error.
 */
JNIEXPORT jint JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeStart(
    JNIEnv*, jclass, jlong h, jint interfaceNumber, jint dataEndpoint, jint maxPacketBytes, jint dataInterval,
    jboolean highSpeed, jint feedbackEndpoint, jint feedbackPacketBytes, jint sampleRate, jint bytesPerFrame,
    jint slotBytes, jint bufferBytes) {
    Device* d = device(h);
    stopStream(d);
    auto* s = new Stream();
    s->handle = d->handle;
    s->interfaceNumber = interfaceNumber;
    s->bytesPerFrame = bytesPerFrame;
    s->slotBytes = slotBytes;
    s->maxFrames = maxPacketBytes / bytesPerFrame;
    s->dataInterval = dataInterval;
    // 8 ms a transfer (64 microframes, or 8 frames at full speed).
    s->packetsPerTransfer = highSpeed ? std::max(1, 64 >> dataInterval) : 8;
    s->transferMs = highSpeed ? (s->packetsPerTransfer << dataInterval) / 8 : s->packetsPerTransfer;
    s->queuedMs = s->transferMs * DATA_TRANSFERS;
    s->nominal = nominalRate(sampleRate, highSpeed);
    s->rate = s->nominal;
    s->ring.resize(std::max(bufferBytes - bufferBytes % bytesPerFrame, bytesPerFrame * 1024));
    d->stream = s;

    for (auto& slot : s->data) {
        slot.stream = s;
        slot.transfer = libusb_alloc_transfer(s->packetsPerTransfer);
        auto* buffer = new uint8_t[s->packetsPerTransfer * maxPacketBytes];
        libusb_fill_iso_transfer(slot.transfer, d->handle, static_cast<unsigned char>(dataEndpoint), buffer,
                                 s->packetsPerTransfer * maxPacketBytes, s->packetsPerTransfer, onData, &slot, 1000);
        fill(&slot);
    }
    if (feedbackEndpoint != 0) {
        for (auto& t : s->feedback) {
            t = libusb_alloc_transfer(1);
            auto* buffer = new uint8_t[feedbackPacketBytes];
            libusb_fill_iso_transfer(t, d->handle, static_cast<unsigned char>(feedbackEndpoint), buffer, feedbackPacketBytes, 1, onFeedback, s, 1000);
            libusb_set_iso_packet_lengths(t, feedbackPacketBytes);
        }
    }
    // Counted before each submit: its callback may come before the submit returns.
    int r = 0;
    for (auto* t : s->feedback) {
        if (!t) continue;
        s->inFlight++;
        if ((r = libusb_submit_transfer(t)) != 0) { s->inFlight--; break; }
    }
    if (r == 0) {
        for (auto& slot : s->data) {
            s->inFlight++;
            if ((r = libusb_submit_transfer(slot.transfer)) != 0) { s->inFlight--; break; }
        }
    }
    if (r != 0) {
        LOGW("start: %s", libusb_error_name(r));
        stopStream(d);
        return r;
    }
    LOGI("streaming: interface %d, %d Hz, %d bytes a frame, %d packets a transfer", interfaceNumber, sampleRate, bytesPerFrame, s->packetsPerTransfer);
    return 0;
}

/** Stops streaming and frees the transfers; the DAC stays claimed. */
JNIEXPORT void JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeStop(JNIEnv*, jclass, jlong h) {
    stopStream(device(h));
}

/** Writes up to [size] bytes from [buffer] at [offset], whole frames: how many went in, or -1 when the DAC is gone. */
JNIEXPORT jint JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeWrite(JNIEnv* env, jclass, jlong h, jobject buffer, jint offset, jint size) {
    Stream* s = device(h)->stream;
    if (s == nullptr || s->lost) return -1;
    auto* from = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer)) + offset;
    std::lock_guard<std::mutex> g(s->lock);
    size_t space = s->ring.size() - s->filled;
    size_t n = std::min(static_cast<size_t>(size), space);
    n -= n % s->bytesPerFrame;
    size_t first = std::min(n, s->ring.size() - s->writeAt);
    memcpy(s->ring.data() + s->writeAt, from, first);
    memcpy(s->ring.data(), from + first, n - first);
    s->writeAt = (s->writeAt + n) % s->ring.size();
    s->filled += n;
    if (n > 0) s->fed = true;
    return static_cast<jint>(n);
}

JNIEXPORT void JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeSetPlaying(JNIEnv*, jclass, jlong h, jboolean playing) {
    Stream* s = device(h)->stream;
    if (s == nullptr) return;
    std::lock_guard<std::mutex> g(s->lock);
    s->playing = playing;
    if (playing) {
        s->fadeTotal = 0;
        s->fadedOut = false;
    }
}

/** Empties the ring; what was already in flight no longer counts as heard. */
JNIEXPORT void JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeFlush(JNIEnv*, jclass, jlong h) {
    Stream* s = device(h)->stream;
    if (s == nullptr) return;
    std::lock_guard<std::mutex> g(s->lock);
    s->readAt = s->writeAt = s->filled = 0;
    s->fed = false;
    s->fadeTotal = 0;
    s->fadedOut = false;
    s->generation++;
}

/** Fades the samples still to go out over [frames], then silence until played again. */
JNIEXPORT void JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeFadeOut(JNIEnv*, jclass, jlong h, jint frames) {
    Stream* s = device(h)->stream;
    if (s == nullptr || frames <= 0) return;
    std::lock_guard<std::mutex> g(s->lock);
    s->fadeTotal = frames;
    s->fadeLeft = frames;
}

/** Frames from the ring the DAC has taken since streaming started. */
JNIEXPORT jlong JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeHead(JNIEnv*, jclass, jlong h) {
    Stream* s = device(h)->stream;
    return s == nullptr ? 0 : s->head.load();
}

/**
 * [underruns, feedback values taken, transfer errors, rate in Q16.16, nominal, lost,
 * longest gap between refills in ms, late refills, ms in flight, the event thread's nice value].
 */
JNIEXPORT jintArray JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeCounts(JNIEnv* env, jclass, jlong h) {
    Stream* s = device(h)->stream;
    jint values[10] = {};
    values[9] = device(h)->eventNice.load();
    if (s != nullptr) {
        values[0] = s->underruns.load();
        values[1] = s->feedbackValues.load();
        values[2] = s->errors.load();
        values[3] = s->rate.load();
        values[4] = s->nominal;
        values[5] = s->lost.load() ? 1 : 0;
        values[6] = s->longestGapMs.load();
        values[7] = s->lateRefills.load();
        values[8] = s->queuedMs;
    }
    jintArray out = env->NewIntArray(10);
    env->SetIntArrayRegion(out, 0, 10, values);
    return out;
}

/**
 * Gives the DAC back to Android: releases [interfaces], lets Android's driver bind them
 * again, and (with [reset]) resets the DAC, which Android takes as a replug and sets the DAC
 * up afresh (0.3.1's test on the S24; reattaching alone left it silent). [attach results..., reset result].
 */
JNIEXPORT jintArray JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeGiveBack(JNIEnv* env, jclass, jlong h, jintArray interfaces, jboolean reset) {
    Device* d = device(h);
    stopStream(d);
    jsize n = env->GetArrayLength(interfaces);
    std::vector<jint> list(n);
    env->GetIntArrayRegion(interfaces, 0, n, list.data());
    std::vector<jint> results;
    for (jint i : list) libusb_release_interface(d->handle, i);
    for (jint i : list) results.push_back(libusb_attach_kernel_driver(d->handle, i));
    results.push_back(reset ? libusb_reset_device(d->handle) : 0);
    jintArray out = env->NewIntArray(static_cast<jsize>(results.size()));
    env->SetIntArrayRegion(out, 0, static_cast<jsize>(results.size()), results.data());
    return out;
}

/** Ends the event thread and closes libusb; the descriptor itself is Android's to close. */
JNIEXPORT void JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeClose(JNIEnv*, jclass, jlong h) {
    Device* d = device(h);
    stopStream(d);
    d->quit = true;
    libusb_interrupt_event_handler(d->context);
    if (d->events.joinable()) d->events.join();
    libusb_close(d->handle);
    libusb_exit(d->context);
    delete d;
}

/** libusb's name for an error code, for the debug screen. */
JNIEXPORT jstring JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbStream_nativeErrorName(JNIEnv* env, jclass, jint code) {
    return env->NewStringUTF(libusb_error_name(code));
}

}  // extern "C"
