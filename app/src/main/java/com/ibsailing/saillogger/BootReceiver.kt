package com.ibsailing.saillogger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log


class BootReceiver : BroadcastReceiver() {
    val TAG = "BootReceiver.kt"
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "BootService onReceive")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
           // val pm = context.packageManager
           // val launchIntent = pm.getLaunchIntentForPackage("com.ibsailing.saillogger")
          //  launchIntent!!.putExtra("intentmessage", "start")
          //  context.startActivity(launchIntent)
        }
    }
}