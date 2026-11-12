package com.mpaulrenlund.audiofool.updates

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.mpaulrenlund.audiofool.AudioFoolApp
import java.io.File

/**
 * Hands a downloaded build to Android's installer (spec 9): one install session with the
 * APK in it, committed; Android answers through [UpdateInstallReceiver]. Android checks
 * the APK is signed with the same key as the app, and asks the user to confirm.
 */
object UpdateInstaller {
    fun commit(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        var committed = false
        try {
            installer.openSession(id).use { session ->
                session.openWrite("AudioFool.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out, 64 * 1024) }
                    session.fsync(out)
                }
                val answer = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
                // Mutable: Android adds the status to it. Explicit, as Android 14 asks of a mutable one.
                val callback = PendingIntent.getBroadcast(context, id, answer, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(callback.intentSender)
                committed = true
            }
        } finally {
            if (!committed) installer.abandonSession(id)
        }
    }

    /** Any session of AudioFool's own still open: left by an earlier run, or by Cancel. */
    fun abandonOwnSessions(context: Context) {
        val installer = context.packageManager.packageInstaller
        for (session in installer.mySessions) runCatching { installer.abandonSession(session.sessionId) }
    }
}

/** Android's installer's answers: show its confirmation, or how it ended. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updates = (context.applicationContext as AudioFoolApp).updates
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)?.let(updates::confirmationNeeded)
            else -> updates.installEnded(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
