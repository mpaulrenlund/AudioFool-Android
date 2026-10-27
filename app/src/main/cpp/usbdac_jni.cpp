// The USB DAC's native side (0.3.0, the look-only build; design/usb-bit-perfect.md).
// Android's Java API can take an interface from the kernel's driver (claimInterface with
// force) but has no way to give it back: USBDEVFS_CONNECT asks the kernel to bind its own
// driver again, so the DAC is an ordinary Android output once more. libusb joins this
// library when the driver is built.

#include <jni.h>
#include <cerrno>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>

extern "C" JNIEXPORT jint JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbDac_reconnect(JNIEnv*, jclass, jint fd, jint interfaceNumber) {
    usbdevfs_ioctl command{};
    command.ifno = interfaceNumber;
    command.ioctl_code = USBDEVFS_CONNECT;
    command.data = nullptr;
    return ioctl(fd, USBDEVFS_IOCTL, &command) < 0 ? errno : 0;
}

// 0.3.1: after 0.3.0's give-back the DAC played silent until a replug (the user, on the S24).
// A USB reset is the nearest thing to a replug an app can ask for.
extern "C" JNIEXPORT jint JNICALL
Java_com_mpaulrenlund_audiofool_playback_UsbDac_reset(JNIEnv*, jclass, jint fd) {
    return ioctl(fd, USBDEVFS_RESET, 0) < 0 ? errno : 0;
}
