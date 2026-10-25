package com.un4seen.bass;

import java.nio.ByteBuffer;
import android.os.ParcelFileDescriptor;

@SuppressWarnings({"all"})
public class BASS_AC3
{
	// BASS_CHANNELINFO type
	public static final int BASS_CTYPE_STREAM_AC3 = 0x11000;

	public static native int BASS_AC3_StreamCreateFile(String file, long offset, long length, int flags);
	public static native int BASS_AC3_StreamCreateFile(ByteBuffer file, long offset, long length, int flags);
	public static native int BASS_AC3_StreamCreateFile(BASS.Asset file, long offset, long length, int flags);
	public static native int BASS_AC3_StreamCreateFile(ParcelFileDescriptor file, long offset, long length, int flags);
	public static native int BASS_AC3_StreamCreateURL(String url, int offset, int flags, BASS.DOWNLOADPROC proc, Object user);
	public static native int BASS_AC3_StreamCreateFileUser(int system, int flags, BASS.BASS_FILEPROCS procs, Object user);
	
	static {
		System.loadLibrary("bass_ac3");
	}
}
