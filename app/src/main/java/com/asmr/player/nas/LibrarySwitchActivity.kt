package com.asmr.player.nas

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.asmr.player.MainActivity

/** Runs in a short-lived process so the next activity receives a fresh application graph. */
class LibrarySwitchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previous = intent.extras?.getBinder("previous")
        fun launchLibrary() {
            startActivity(Intent.makeRestartActivityTask(ComponentName(this, MainActivity::class.java)))
            finishAndRemoveTask()
            Process.killProcess(Process.myPid())
        }
        if (previous == null || !previous.isBinderAlive) {
            launchLibrary()
            return
        }
        try {
            previous.linkToDeath({ runOnUiThread { launchLibrary() } }, 0)
            Process.killProcess(intent.getIntExtra("pid", -1).also { require(it > 0) })
        } catch (_: android.os.RemoteException) {
            launchLibrary()
        }
    }

    companion object {
        fun launch(context: Context) {
            val extras = Bundle().apply {
                putBinder("previous", Binder())
                putInt("pid", Process.myPid())
            }
            context.startActivity(Intent(context, LibrarySwitchActivity::class.java)
                .putExtras(extras).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
    }
}
