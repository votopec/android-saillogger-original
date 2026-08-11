package com.ibsailing.saillogger

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

class CyclopsService:Service() {
private val TAG="CyclopsService"

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "OnBind")
        return LocalBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)

    }


    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        val service: CyclopsService
            get() =// Return this instance of LocalService so clients can call public methods
                this@CyclopsService
    }
}