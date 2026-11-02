package app.omnitask.widget

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import app.omnitask.MainActivity
import app.omnitask.data.VoiceRec

/**
 * The mic on the lock card: starts a voice note without unlocking the phone (owner, 2 Nov: "Can we bypass lock
 * screen?" then "Ok then try").
 *
 * It may show over the lock screen and finishes at once, so nothing of the notes is shown there. The recording runs in
 * [app.omnitask.data.VoiceRecordService] as from the app; its notification has the stop button, and the recording
 * waits in the app's voice list until the app is opened, where it is written down and reviewed as always.
 * Without the mic permission it opens the app instead, which asks for it (that needs the phone unlocked).
 */
class LockVoiceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setShowWhenLocked(true)
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Started while this activity is in front, so the phone lets the service use the mic.
            VoiceRec.start(applicationContext)
        } else {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_VOICE),
            )
        }
        finish()
    }
}
