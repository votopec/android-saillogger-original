package com.ibsailing.saillogger

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.location.Location
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.findNavController


class ViewModelMain: ViewModel() {

    // Accelerometer and magnetometer sensors, as retrieved from the
    // sensor manager.
  
       

    lateinit var sharedPref: SharedPreferences

    
    var eventsPerQr=5
    var currentLocation = Location("gps")

lateinit var pm:PowerManager

    lateinit var loggingFragment: LoggingFragment
    lateinit var foregroundService: ForegroundService


      lateinit var serviceIntent: Intent


      //Bluetooth Cyclops
   // lateinit var blManager: BluetoothManager
   // lateinit var bluetoothAdapter: BluetoothAdapter
   // lateinit var bluetoothLeScanner: BluetoothLeScanner
    val blueToothDevicesList=ArrayList<BluetoothDevice>()
    val gattList=ArrayList<BluetoothGatt>()
    val characteristicList= ArrayList<BluetoothGattCharacteristic>()

    var isLoggingHeel=true
    var isLoggingPitch=true
    var isLoggingMagnetometer=false
    var isLoggingHeading=false
    var isLoggingAltitude=false
    var isLoggingCyclops=false

    var phoneTimeToGpsOffset=0L

    var extraHeelOffset=0.0
    var heelOffset = 0.0

    var extraPitchOffset=0.0
     var pitchOffset = 0.0
     var boatNo = 1
     var teamName = "BBDH"
     var zeroPitchPressedTime: Long = 0
    var zeroHeelPressedTime: Long = 0

    var zeroPitchPressed = false
     var zeroHeelPressed = false
     var logging = false
    var serviceRunning=false

    var backgroundLocationAllowed=false
    var locationAllowed=false

    var heading = 0.0
    var pitch = 0.0
    var heel = 0.0
    var rotation = 0
    var counter = 0
     var updateInterval = 1000
     var mAccelerometerData = FloatArray(3)
     var mMagnetometerData = FloatArray(3)
    var mGameRotationData=FloatArray(3)

    var mBound = false

    var dontShowBattery=false

    var logEventList= ArrayList<LogEvent>()

    init{

        Log.d(TAG,"viewmodel init mbound=$mBound")
    }




companion object{
    const val TAG="ViewModelMain"
    const val BOAT_NO = "BOATNUMBER"
    const val TEAM_NAME = "TEAMNAME"
    const val DONTSHOWAGAINBATTERY = "BATDONTSHOW"
    const val GPSTIMEOFFSET = "GPSTIMEOFFSET"
    const val ROTATION = "Rotation"
    const val PITCH_OFFSET = "PitchOffset"
    const val EXTRA_PITCH_OFFSET = "ExtraPitchOffset"

    const val HEEL_OFFSET = "HeelOffset"
    const val EXTRA_HEEL_OFFSET = "ExtraHeelOffset"

    const val LOG_ALTITUDE="LogAltitude"
    const val LOG_HEEL="LogHeel"
    const val LOG_PITCH="LogPitch"
    const val LOG_MAGNET="LogMagnet"
    const val LOG_HEADING="LogHeading"
    const val EVENTS_PER_QR="EventsPerQr"


    const val UPDATE_INTERVAL = "UpdateInterval"

}

    /** Defines callbacks for service binding, passed to bindService()  */
    val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            Log.d(TAG, "onServiceConnected")
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as ForegroundService.LocalBinder
            foregroundService = binder.service
            Log.d(TAG, "got foreground service, isrunning: ${foregroundService.isRunning}, has points:${foregroundService.logPointList.size}")
            foregroundService.stopService = false
            mBound = true
            if (foregroundService.isRunning){
                serviceRunning = true
                logging = true
                Log.d(TAG, "Service is running, starting logging fragment")
            } else {
                Log.d(TAG, "Service is not running")
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
            Log.d(TAG, "onServiceDisconnected")
            if (hasForegroundService()) {
                Log.d(TAG, "Service is running ${foregroundService.isRunning}")
            }

        }
    }

    fun hasForegroundService(): Boolean = ::foregroundService.isInitialized

    fun savePrefs(){
        sharedPref.edit().putInt(BOAT_NO, boatNo).apply()
        sharedPref.edit().putInt(UPDATE_INTERVAL, updateInterval).apply()
        sharedPref.edit().putString(TEAM_NAME, teamName).apply()
        sharedPref.edit().putFloat(EXTRA_PITCH_OFFSET, extraPitchOffset.toFloat()).apply()
        sharedPref.edit().putFloat(EXTRA_HEEL_OFFSET, extraHeelOffset.toFloat()).apply()
        sharedPref.edit().putFloat(PITCH_OFFSET, pitchOffset.toFloat()).apply()
        sharedPref.edit().putFloat(HEEL_OFFSET, heelOffset.toFloat()).apply()
        sharedPref.edit().putBoolean(LOG_HEEL, isLoggingHeel).apply()
        sharedPref.edit().putBoolean(LOG_PITCH, isLoggingPitch).apply()
        sharedPref.edit().putBoolean(LOG_ALTITUDE, isLoggingAltitude).apply()
        sharedPref.edit().putBoolean(LOG_HEADING, isLoggingHeading).apply()
        sharedPref.edit().putBoolean(LOG_MAGNET, isLoggingMagnetometer).apply()
        sharedPref.edit().putInt(EVENTS_PER_QR, eventsPerQr).apply()
        sharedPref.edit().putLong(GPSTIMEOFFSET,phoneTimeToGpsOffset).apply()
    }
    
    fun loadPrefs(){
        dontShowBattery = sharedPref.getBoolean(DONTSHOWAGAINBATTERY, false)
        boatNo = sharedPref.getInt(BOAT_NO, 1)
        rotation = sharedPref.getInt(ROTATION, 0)
        heelOffset = sharedPref.getFloat(HEEL_OFFSET, 0F).toDouble()
        extraHeelOffset=sharedPref.getFloat(EXTRA_HEEL_OFFSET,0F).toDouble()

        phoneTimeToGpsOffset=sharedPref.getLong(GPSTIMEOFFSET,0L)

        pitchOffset = sharedPref.getFloat(PITCH_OFFSET, 0F).toDouble()
        extraPitchOffset=sharedPref.getFloat(EXTRA_PITCH_OFFSET,0F).toDouble()
        updateInterval = sharedPref.getInt(UPDATE_INTERVAL, 1000)
        teamName = sharedPref.getString(TEAM_NAME, "Unkn").toString()

        isLoggingHeel=sharedPref.getBoolean(LOG_HEEL, true)
        isLoggingPitch=sharedPref.getBoolean(LOG_PITCH, true)
        isLoggingMagnetometer=sharedPref.getBoolean(LOG_MAGNET, false)
        isLoggingAltitude=sharedPref.getBoolean(LOG_ALTITUDE, false)
        isLoggingHeading=sharedPref.getBoolean(LOG_HEADING, false)

        eventsPerQr=sharedPref.getInt(EVENTS_PER_QR, 10)
    }

    fun fillIntent() {
        serviceIntent.putExtra(ROTATION,rotation)
       serviceIntent.putExtra(PITCH_OFFSET,pitchOffset)
       serviceIntent.putExtra(EXTRA_PITCH_OFFSET,extraPitchOffset)
       serviceIntent.putExtra(HEEL_OFFSET,heelOffset)
       serviceIntent.putExtra(EXTRA_HEEL_OFFSET,extraHeelOffset)
       serviceIntent.putExtra(UPDATE_INTERVAL,updateInterval)
       serviceIntent.putExtra(BOAT_NO,boatNo)
       serviceIntent.putExtra(TEAM_NAME,teamName)
       serviceIntent.putExtra(LOG_HEEL,isLoggingHeel)
       serviceIntent.putExtra(LOG_PITCH,isLoggingPitch)
       serviceIntent.putExtra(LOG_HEADING,isLoggingHeading)
       serviceIntent.putExtra(LOG_ALTITUDE,isLoggingAltitude)
       serviceIntent.putExtra(LOG_MAGNET,isLoggingMagnetometer)
       serviceIntent.putExtra(GPSTIMEOFFSET,phoneTimeToGpsOffset)
    }
}
