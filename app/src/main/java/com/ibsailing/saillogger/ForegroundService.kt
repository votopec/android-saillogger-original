package com.ibsailing.saillogger

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import kotlin.math.atan
import kotlin.math.roundToLong
import kotlin.math.sqrt


// foreground service to do logging
class ForegroundService : Service(), LocationListener, SensorEventListener {


    private var wakeLock: PowerManager.WakeLock? = null

    val notificationId = 12345
    private var debugStringBuilder = StringBuilder()

    //Managers
    private var locManager: LocationManager? = null
    private var mSensorManager: SensorManager? = null

    //Handler for updating log points
    private var mHandler: Handler? = null
    var stopService = false

    //number of locations logged
    var locCounter = 0
    var loggedPointCounter = 0L
    var logStartTimestamp = 0L
    var lastLogTimestamp = 0L
    var isRunning = false

//    logging data
    var isLoggingHeel=true
    var isLoggingPitch=true
    var isLoggingAltitude=false
    var isLoggingHeading=false
    var isLoggingMagnetometer=false

    lateinit var mNotificationManager: NotificationManager
    lateinit var notificationBuilder: NotificationCompat.Builder
    private val logDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SailLoggerLogWriter")
    }.asCoroutineDispatcher()
    private val serviceScope = CoroutineScope(SupervisorJob() + logDispatcher)
    private var durableLogWriter: DurableLogWriter? = null

    private var isLocationWritten = true
    private var phoneTimeToGpsOffset = 0L


    val logPointList = ArrayList<LogPoint>()
    private var heelList: MutableList<Double> = ArrayList()
    private var pitchList: MutableList<Double> = ArrayList()
    private var headingList: MutableList<Double> = ArrayList()



    private var heelOffset = 0.0
    private var extraHeelOffset=0.0
    private var pitchOffset = 0.0
    private var extraPitchOffset=0.0
    private var boatNo = 2
    private var teamName = "Team"
    var maxSpeed = 0f

    private lateinit var saveDebugFile: File

    // Binder given to clients
    private val binder: IBinder = LocalBinder()


    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        val service: ForegroundService
            get() =// Return this instance of LocalService so clients can call public methods
                this@ForegroundService
    }

    private var resetPitchHeelAverage = true
    private var resetHeadingAverage = true

    var pitch = 0.0
    var heel = 0.0
    var rotation = 0
    private var updateInterval = 1000
    private var mAccelerometerData = FloatArray(3)
    private var mMagnetometerData = FloatArray(3)
    var currentLocation = Location("gps")
    var lastLocationTime = 0L
    var lastSensorTime = 0L
    var counter = 0
    override fun onCreate() {
        Log.d(TAG, "OnCreate")
        super.onCreate()
        locManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val fileName=getDebugFileName(System.currentTimeMillis())
        saveDebugFile = File(applicationContext.getExternalFilesDir("debug"), fileName)
        debugStringBuilder.append("\n${getTimeString(System.currentTimeMillis())}")
        debugStringBuilder.append("\nOnCreate")
    }





    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "On start command")
          debugStringBuilder.append("\n${getTimeString(System.currentTimeMillis())}")
        debugStringBuilder.append("\nOn start command, id:$startId")
        debugStringBuilder.append("\nIntent: $intent")

        saveDebugFile()

        if (intent == null) {
            Log.w(TAG, "Service restart delivered without an intent")
            stopSelf(startId)
            return START_REDELIVER_INTENT
        }

        if (isRunning) {
            Log.d(TAG, "Logger service is already running; ignoring duplicate start")
            return START_REDELIVER_INTENT
        }

        val action = intent.action
        Log.d(TAG, "using an intent with action $action")

        pitchOffset = intent.getDoubleExtra(ViewModelMain.PITCH_OFFSET, 0.0)
        extraPitchOffset=intent.getDoubleExtra(ViewModelMain.EXTRA_PITCH_OFFSET, 0.0)
        extraHeelOffset=intent.getDoubleExtra(ViewModelMain.EXTRA_HEEL_OFFSET, 0.0)


        heelOffset = intent.getDoubleExtra(ViewModelMain.HEEL_OFFSET, 0.0)
        updateInterval = intent.getIntExtra(ViewModelMain.UPDATE_INTERVAL, 1000)
        rotation = intent.getIntExtra(ViewModelMain.ROTATION, 0)
        boatNo = intent.getIntExtra(ViewModelMain.BOAT_NO, 1)
        teamName = intent.getStringExtra(ViewModelMain.TEAM_NAME).toString()

        phoneTimeToGpsOffset=intent.getLongExtra(ViewModelMain.GPSTIMEOFFSET,0L)

        isLoggingHeel=intent.getBooleanExtra(ViewModelMain.LOG_HEEL,true)
        isLoggingPitch=intent.getBooleanExtra(ViewModelMain.LOG_PITCH,true)
        isLoggingHeading=intent.getBooleanExtra(ViewModelMain.LOG_HEADING,false)
        isLoggingAltitude=intent.getBooleanExtra(ViewModelMain.LOG_ALTITUDE,false)
        isLoggingMagnetometer=intent.getBooleanExtra(ViewModelMain.LOG_MAGNET,false)



        // we need this lock so our service gets not affected by Doze Mode
        releaseWakeLock()
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SailLogger:ForegroundServiceWakeLockTag").apply {
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }


        logPointList.clear()
        pitchList.clear()
        heelList.clear()
        headingList.clear()
        loggedPointCounter = 0L
        logStartTimestamp = 0L
        lastLogTimestamp = 0L
        isRunning = true
        stopService = false
        lastLocationTime = 0L
        maxSpeed = 0f

        // stuff to start the notification
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        notificationIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP

        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
        notificationBuilder.setContentTitle("Logging data").setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentText("Sensor Logger is running").setSmallIcon(R.drawable.ic_skiff_notification)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(notificationId, notificationBuilder.build())
       }

        // start location listening
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locManager!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
        }
        locCounter = 0
        // start heel pitch sensor
        mSensorManager = getSystemService(
            SENSOR_SERVICE
        ) as SensorManager
        val mSensorAccelerometer = mSensorManager!!.getDefaultSensor(
            Sensor.TYPE_ACCELEROMETER
        )
        val mSensorMagnetometer = mSensorManager!!.getDefaultSensor(
            Sensor.TYPE_MAGNETIC_FIELD
        )


        if (mSensorAccelerometer != null) {
            mSensorManager?.registerListener(
                this, mSensorAccelerometer,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
        if (mSensorMagnetometer != null) {
            mSensorManager?.registerListener(
                this, mSensorMagnetometer,
                SensorManager.SENSOR_DELAY_GAME
            )
        }


        //start listening timer
        mHandler = Handler(Looper.getMainLooper())
        mHandler?.postDelayed(mUpdateTimeTask, updateInterval.toLong())

        return START_REDELIVER_INTENT
    }













    fun addPointsToFile() {
        debugStringBuilder.append("\nSaved: ${getTimeString(System.currentTimeMillis())}")
        flushLogWriter(force = true)
    }



    fun stopService() {
        debugStringBuilder.append("\n${getTimeString(System.currentTimeMillis())}")
        debugStringBuilder.append("\nStopService")
        saveDebugFile()

        cleanupLoggingResources()
        finishLogWriter()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }


    override fun onDestroy() {
        Log.d(TAG, "On destroy")
        debugStringBuilder.append("\n${getTimeString(System.currentTimeMillis())}")
        debugStringBuilder.append("\nOnDestroy")
        saveDebugFile()
        val wasRunning = isRunning
        cleanupLoggingResources()
        if (wasRunning) {
            closeLogWriterKeepingRecovery()
        }
        serviceScope.cancel()
        logDispatcher.close()

        super.onDestroy()
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.d(TAG, "OnUnbind")
          debugStringBuilder.append("\n${getTimeString(System.currentTimeMillis())}")
        debugStringBuilder.append("\nOnUnbind")
        saveDebugFile()

        return super.onUnbind(intent)
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "OnBind")
          debugStringBuilder.append("\n${getTimeString(System.currentTimeMillis())}")
        debugStringBuilder.append("\nOnBind")
        saveDebugFile()

        return binder
    }

    // creating notification channel
    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        serviceChannel.setSound(null, null)
        mNotificationManager = getSystemService(NotificationManager::class.java)
        mNotificationManager.createNotificationChannel(serviceChannel)
    }

    //Check if time of phone is wrong, use GPS time to fix it
    //remember the location and mark it as not written
    override fun onLocationChanged(location: Location) {
        phoneTimeToGpsOffset = ((System.currentTimeMillis() - location.time) / 1000) * 1000
        val lp = LogPoint()
        lp.timeStamp = location.time
        lp.location = Location(location)



       if(mMagnetometerData.size>=3) {
           lp.magnetX = mMagnetometerData[0]
           lp.magnetY = mMagnetometerData[1]
           lp.magnetZ = mMagnetometerData[2]
       }





//   Get Median Heading and write it
        if (headingList.isNotEmpty()) {
            headingList.sort()
            lp.heading = headingList[headingList.size / 2]
            resetHeadingAverage = true
        }


        recordLogPoint(lp)

        //Write number of points to notification

        if (loggedPointCounter > 0 && !isLoggingPitch &&!isLoggingHeel){
            val loggingDuration = lastLogTimestamp - logStartTimestamp
            val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(loggingDuration), ZoneId.ofOffset("", ZoneOffset.UTC))
            val timeString = date.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            notificationBuilder.setContentText("SailLogger: ${timeString}, ${loggedPointCounter} points")
            mNotificationManager.notify(notificationId, notificationBuilder.build())
        }


        //save file every 60 seconds no matter what
        if (location.time - lastLocationTime > 60000) {
              debugStringBuilder.append("\n${getTimeString(System.currentTimeMillis())}")
            debugStringBuilder.append("\nLoc:${location.time}")
            saveDebugFile()
            addPointsToFile()
            // saveCSV(incomplete = true)
            lastLocationTime = location.time
        }
        locCounter++
        currentLocation = location
        if (location.speed > maxSpeed) maxSpeed = location.speed
        isLocationWritten = false
    }

    // when sensor changes, add to list of numbers to calculate average from
    override fun onSensorChanged(sensorEvent: SensorEvent) {
        when (sensorEvent.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                mAccelerometerData = sensorEvent.values.clone()
                lastSensorTime = SystemClock.elapsedRealtimeNanos()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> mMagnetometerData = sensorEvent.values.clone()

            else -> return
        }


        // if point was written, reset average collecting
        if (resetPitchHeelAverage) {
            counter = 0
            pitchList = ArrayList()
            heelList = ArrayList()
            resetPitchHeelAverage = false
        }

        if (resetHeadingAverage) {
            counter = 0
            headingList = ArrayList()
            resetHeadingAverage = false
        }

        // get values from the sensor data
        val rotationMatrix = FloatArray(9)
        val rotationOK = SensorManager.getRotationMatrix(
            rotationMatrix,
            null, mAccelerometerData, mMagnetometerData
        )
        var instantHeading = 0.0
        var instantHeel: Double
        var instantPitch: Double
        val orientationValues = FloatArray(3)
        if (rotationOK) {
            SensorManager.getOrientation(rotationMatrix, orientationValues)
            instantHeading = (180 + Math.toDegrees(orientationValues[0].toDouble()))
            instantPitch = (Math.toDegrees(orientationValues[1].toDouble()))
            instantHeel = (Math.toDegrees(orientationValues[2].toDouble()))
        } else {
            // Most chances are that there are no magnet datas
            val gx = mAccelerometerData[0] / 9.81f
            val gy = mAccelerometerData[1] / 9.81f
            val gz = mAccelerometerData[2] / 9.81f
            val pitch = -atan(gy / sqrt((gx * gx + gz * gz).toDouble()))
            val roll = -atan(gx / sqrt((gy * gy + gz * gz).toDouble()))
            Log.d(TAG, "Pitch $pitch, roll $roll")
            //get values to be shown at the screen, they update in handler runnable
            instantPitch = Math.toDegrees(pitch)
            instantHeel = Math.toDegrees(roll)
        }


        val op = OrientationParams(instantPitch, instantHeel, instantHeading).rotate(rotation)
        instantPitch = op.pitch
        instantHeel = op.heel
        instantHeading = op.heading        // flip the data based on the set rotation of the phone


        // offset pitch and heel to zeroed values
        instantPitch = (instantPitch + pitchOffset+extraPitchOffset)
        instantHeel = (instantHeel + heelOffset+extraHeelOffset)

        // add them to list used to calculate average
        headingList.add(instantHeading)
        pitchList.add(instantPitch)
        heelList.add(instantHeel)
        counter++


    }

    // called at the updateInterval rate of points
    private val mUpdateTimeTask = object : Runnable {
        override fun run() {


            if (stopService || !isRunning) {
                return
            }

            mHandler?.postDelayed(this, updateInterval.toLong())

            if (isLoggingHeel || isLoggingPitch) {
                val uptime = System.currentTimeMillis()
                var time = uptime - phoneTimeToGpsOffset

                // make time round number to remove small differences
                time = (time.toDouble() / updateInterval).roundToLong() * updateInterval

                // create point and add to the list
                val lp = LogPoint()

                //Write number of points to notification
                if (loggedPointCounter > 0 && (loggedPointCounter * updateInterval) % 1000 == 0L) {
                    val loggingDuration = lastLogTimestamp - logStartTimestamp
                    val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(loggingDuration), ZoneId.ofOffset("", ZoneOffset.UTC))
                    val timeString = date.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    notificationBuilder.setContentText("SailLogger: ${timeString}, ${loggedPointCounter} points")
                    mNotificationManager.notify(notificationId, notificationBuilder.build())
                }

                // calculate medians of pitch and heel collected
                if (pitchList.isNotEmpty()) {
                    pitchList.sort()
                    pitch = pitchList[pitchList.size / 2]
                    lp.pitch = pitch
                }

                if (heelList.isNotEmpty()) {
                    heelList.sort()
                    heel = heelList[heelList.size / 2]
                    lp.heel = heel
                }

                resetPitchHeelAverage = true
                lp.timeStamp = time

                if ((!isLoggingPitch || lp.pitch != null) && (!isLoggingHeel || lp.heel != null)) {
                    recordLogPoint(lp)
                }
            }
        }
    }


    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun getTimeString(timeStamp: Long): String {
        val timeString: String
        val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeStamp), ZoneId.systemDefault())
        timeString = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss.SSS"))
        return timeString
    }



    private fun getDebugFileName(timeStamp: Long): String {
        val timeString: String
        val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeStamp), ZoneId.systemDefault())
        timeString = date.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return "$timeString.txt"
    }


    companion object {
        const val CHANNEL_ID = "ForegroundServiceChannel"
        private const val TAG = "ForegroundService"
        private const val WAKE_LOCK_TIMEOUT_MS = 24L * 60L * 60L * 1000L
        private const val MAX_RECENT_LOG_POINTS = 2000
    }

    override fun onProviderDisabled(provider: String) {
        Log.d(TAG, "On Provider Disabled")
         
          debugStringBuilder.append("\n${getTimeString(System.currentTimeMillis())}")
        debugStringBuilder.append("\nOnProviderDisabled")
        saveDebugFile()

    }

    override fun onProviderEnabled(provider: String) {
        Log.d(TAG, "On Provider Enabled")
          debugStringBuilder.append("\n${getTimeString(System.currentTimeMillis())}")
        debugStringBuilder.append("\nOnProviderEnabled")
        saveDebugFile()

    }




    private fun saveDebugFile() {
        val debugText = debugStringBuilder.toString()
        debugStringBuilder.clear()
        if (debugText.isBlank()) {
            return
        }
        serviceScope.launch {
            val fileWriter = FileWriter(saveDebugFile, true)
            fileWriter.write(debugText)
            fileWriter.close()
        }
    }

    private fun cleanupLoggingResources() {
        mSensorManager?.unregisterListener(this)
        locManager?.removeUpdates(this)
        mHandler?.removeCallbacksAndMessages(null)
        mHandler = null
        releaseWakeLock()
        isRunning = false
        stopService = true
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun recordLogPoint(logPoint: LogPoint) {
        if (logStartTimestamp == 0L) {
            logStartTimestamp = logPoint.timeStamp
        }
        lastLogTimestamp = logPoint.timeStamp
        loggedPointCounter++

        logPointList.add(logPoint)
        if (logPointList.size > MAX_RECENT_LOG_POINTS) {
            logPointList.removeAt(0)
        }

        val csvLine = logPoint.getCsvString(
            isLoggingHeel,
            isLoggingPitch,
            isLoggingHeading,
            isLoggingAltitude,
            isLoggingMagnetometer
        )
        val hasGps = logPoint.location != null
        serviceScope.launch {
            writerFor(logPoint.timeStamp).appendRow(csvLine, hasGps, logPoint.timeStamp)
        }
    }

    private fun writerFor(startTimestamp: Long): DurableLogWriter {
        durableLogWriter?.let { return it }
        val outputDir = applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir
        val writer = DurableLogWriter(
            outputDir,
            DurableLogConfig(
                startTimestamp = startTimestamp,
                boatNo = boatNo,
                teamName = teamName,
                updateInterval = updateInterval,
                deviceName = "${Build.BRAND} ${Build.MODEL}",
                androidSdk = Build.VERSION.SDK_INT,
                isLoggingHeel = isLoggingHeel,
                isLoggingPitch = isLoggingPitch,
                isLoggingHeading = isLoggingHeading,
                isLoggingAltitude = isLoggingAltitude,
                isLoggingMagnetometer = isLoggingMagnetometer,
            )
        )
        writer.start()
        durableLogWriter = writer
        return writer
    }

    private fun flushLogWriter(force: Boolean) {
        runBlocking {
            withContext(logDispatcher) {
                durableLogWriter?.flush(force = force)
            }
        }
    }

    private fun finishLogWriter() {
        runBlocking {
            withContext(logDispatcher) {
                durableLogWriter?.finish()
                durableLogWriter = null
            }
        }
    }

    private fun closeLogWriterKeepingRecovery() {
        runBlocking {
            withContext(logDispatcher) {
                durableLogWriter?.flush(force = true)
                durableLogWriter?.close()
                durableLogWriter = null
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        debugStringBuilder.append("\n${getTimeString(System.currentTimeMillis())}")
        debugStringBuilder.append("\nOnTaskRemoved")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        debugStringBuilder.append("\n${getTimeString(System.currentTimeMillis())}")
        debugStringBuilder.append("\nOnLowMemory")
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        debugStringBuilder.append("\n${getTimeString(System.currentTimeMillis())}")
        debugStringBuilder.append("\nOnRebind")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        debugStringBuilder.append("\n${getTimeString(System.currentTimeMillis())}")
        debugStringBuilder.append("\nOnTrimMemory")
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }
}
