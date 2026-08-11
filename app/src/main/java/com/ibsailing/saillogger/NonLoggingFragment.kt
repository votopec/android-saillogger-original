package com.ibsailing.saillogger

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.ibsailing.saillogger.databinding.FragmentNonLoggingBinding
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.roundToInt
import kotlin.math.sqrt


private lateinit var viewModel:ViewModelMain

val heelList=ArrayList<Double>()
val pitchList=ArrayList<Double>()


//Sensors and managers
private var mSensorAccelerometer: Sensor?=null
private var mSensorMagnetometer: Sensor?=null
private var mSensorManager: SensorManager?=null
private var locManager: LocationManager? = null

//If we got the first GPS location
private var hasFirstLocation=false
private var locationsAcquired=0

//If phone time is quite off the GPS time
private var timeOffsetWarningShown=false


//For screen refreshing
private var screenRefreshHandler: Handler? = null


//Permissions needed on all devices. Later asks for background if >Android Q
var permissionsNeeded= mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)


class NonLoggingFragment : Fragment(), SensorEventListener,LocationListener, DialogInterface.OnDismissListener {
    private lateinit var binding: FragmentNonLoggingBinding



    //listener for location permission request
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        Log.d(TAG, "location result: permissions -> ${permissions.keys}")
        Log.d(TAG, "location result: permissions -> ${permissions.values}")

        when {
            //If got fine location
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                viewModel.locationAllowed = true
                startLocManager()
                Log.d(TAG, "Precise location access granted.")
            }

            else -> {
                //If not, is not possible to get GPS data
                Log.d(TAG, " No fine location access granted.")
                AlertDialog.Builder(requireContext())
                    .setTitle("No GPS Location Allowed")
                    .setMessage("No GPS permission allowed. App will log only Heel/Pitch and Heading data.\nPlease allow the app to use the background location...")
                    .setPositiveButton("OK") { _, _ -> }
                    .show()
                viewModel.locationAllowed = false
                viewModel.backgroundLocationAllowed = false
            }
        }
    }


    //Listener for backgroundlocation request
    private val backGroundLocationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        Log.d(TAG, "location result: permissions -> ${permissions.keys}")
        Log.d(TAG, "location result: permissions -> ${permissions.values}")

        when {
            //Permission allowed
            permissions.getOrDefault(Manifest.permission.ACCESS_BACKGROUND_LOCATION, false) -> {
                Log.d(TAG, "BACKGROUND location access granted.")
                viewModel.backgroundLocationAllowed = true
                binding.statusTextview.text = getString(R.string.gps_allowed)
            }

            else -> {
                //Permission not allowed
                viewModel.backgroundLocationAllowed = false
                AlertDialog.Builder(requireContext())
                    .setTitle("No background GPS Location Allowed")
                    .setMessage("No background GPS permission allowed. App will log only Heel/Pitch and Heading data.\nGPS will be logged only while app is visible on screen.\nPlease allow the app to use the background location...")
                    .setPositiveButton("OK") { _, _ -> }
                    .show()
                Log.d(TAG, " No bckgrd location access granted.")
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        viewModel = ViewModelProvider(requireActivity())[ViewModelMain::class.java]

        // handler
        screenRefreshHandler = Handler(Looper.getMainLooper())

        // Get accelerometer and magnetometer sensors from the sensor manager.
        // The getDefaultSensor() method returns null if the sensor
        // is not available on the device.
        mSensorManager = requireActivity().getSystemService(AppCompatActivity.SENSOR_SERVICE) as SensorManager

        mSensorAccelerometer = mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (mSensorAccelerometer == null) {
            Log.d(TAG, "Sensor.TYPE_ACCELEROMETER=null")
        }
        mSensorMagnetometer = mSensorManager!!.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (mSensorMagnetometer == null) {
            Log.d(TAG, "Sensor.TYPE_MAGNETOMETER=null")
        }

        viewModel.currentLocation = Location("gps")

        locManager = requireActivity().getSystemService(AppCompatActivity.LOCATION_SERVICE) as LocationManager
        if (locManager == null) {
            Log.d(TAG, "LocationManager=null")
        }

        viewModel.serviceIntent = Intent(requireContext(), ForegroundService::class.java)
        requireContext().bindService(viewModel.serviceIntent, viewModel.connection, AppCompatActivity.BIND_AUTO_CREATE)


    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNonLoggingBinding.inflate(inflater, container, false)
        return binding.root        // Inflate the layout for this fragment
    }


    override fun onResume() {
        super.onResume()

        hasFirstLocation = false
        locationsAcquired = 0


        //If app doesn't have permissions needed, ask for them
        if (!hasPermissions(requireContext(), permissionsNeeded)){
            AlertDialog.Builder(requireContext())
                .setTitle("Sensitive permissions needed")
                .setMessage(getString(R.string.location_rationale))
                .setPositiveButton("OK") { _, _ ->
                    Log.d(TAG, "OK clicked in dialog")
                    locationPermissionRequest.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                    Log.d(TAG, "locationPermissionRequest.launch executed")
                }.setNegativeButton("Exit") { _, _ -> requireActivity().finish() }.show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (!hasPermissions(requireContext(), mutableListOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Background Position")
                        .setMessage("App needs permission to get the GPS in the background for successful logging.\nPlease 'Allow All The Time' on next screen")
                        .setPositiveButton("OK") { _, _ -> backGroundLocationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) }
                        .show()
                }
            }


            viewModel.backgroundLocationAllowed = true
            viewModel.locationAllowed = true
        }
        if (mSensorAccelerometer != null) {
            mSensorManager!!.registerListener(
                this, mSensorAccelerometer,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
        if (mSensorMagnetometer != null) {
            mSensorManager!!.registerListener(
                this, mSensorMagnetometer,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        //If it has permissions
        startLocManager()
        screenRefreshHandler!!.postDelayed(mUpdateTimeTask, 1000)

    }


    override fun onPause() {
        super.onPause()
//Remove updates, get them back once in foreground again
        locManager?.removeUpdates(this)
        mSensorManager?.unregisterListener(this)
        screenRefreshHandler?.removeCallbacks(mUpdateTimeTask)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        //Listeners for all buttons
        binding.nonLoggingMarkButton.setOnClickListener(markButtonListener)
        binding.buttonStartLogging.setOnClickListener{
            startLogging()
            navigateFromNonLogging(R.id.action_nonLoggingFragment_to_loggingFragment)
        }
        binding.buttonShare.setOnClickListener{  navigateFromNonLogging(R.id.action_nonLoggingFragment_to_shareFragment)}
        binding.zeroHeelButton.setOnClickListener{
            viewModel.zeroHeelPressed = true
            viewModel.zeroHeelPressedTime = System.currentTimeMillis()
        }
        binding.buttonZeroPitch.setOnClickListener{
            viewModel.zeroPitchPressed = true
            viewModel.zeroPitchPressedTime = System.currentTimeMillis()
        }
        binding.buttonRotate.setOnClickListener(rotateButtonListener)
        binding.settingsButton.setOnClickListener{
            SettingsDialogFragment().show(
            childFragmentManager, "SettingsFragmentTag"
        )}


        fillTextViews()


        //Refresh screen every 1000ms
        rotateViews()

        if (viewModel.logging) {
            navigateFromNonLogging(R.id.action_nonLoggingFragment_to_loggingFragment)
        }
    }

    companion object {

        private const val TAG = "NonLoggingFragment"
        const val PERMISSION_CODE = 50


    }



    private fun rotateViews() {


        when (viewModel.rotation) {
            MainActivity.BOWBOTTOM -> {
                binding.bottomTextView.text = getString(R.string.bow)
                binding.topTextView.text = getString(R.string.stern)
                binding.leftTextView.text = getString(R.string.stbd)
                binding.rightTextView.text = getString(R.string.port)
                binding.boatImageView.rotation = 270F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_top, viewModel.boatNo))

            }

            MainActivity.BOWTOP -> {
                binding.bottomTextView.text = getString(R.string.stern)
                binding.topTextView.text = getString(R.string.bow)
                binding.leftTextView.text = getString(R.string.port)
                binding.rightTextView.text = getString(R.string.stbd)
                binding.boatImageView.rotation = 90F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_top, viewModel.boatNo))


            }

            MainActivity.BOWLEFT -> {
                binding.bottomTextView.text = getString(R.string.port)
                binding.topTextView.text = getString(R.string.stbd)
                binding.leftTextView.text = getString(R.string.bow)
                binding.rightTextView.text = getString(R.string.stern)
                binding.boatImageView.rotation = 0F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_top, viewModel.boatNo))


            }

            MainActivity.BOWRIGHT -> {
                binding.bottomTextView.text = getString(R.string.stbd)
                binding.topTextView.text = getString(R.string.port)
                binding.leftTextView.text = getString(R.string.stern)
                binding.rightTextView.text = getString(R.string.bow)
                binding.boatImageView.rotation = 180F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_top, viewModel.boatNo))


            }

            MainActivity.UPRIGHT -> {
                binding.bottomTextView.text = getString(R.string.stbd)
                binding.topTextView.text = getString(R.string.port)
                binding.leftTextView.text = getString(R.string.down)
                binding.rightTextView.text = getString(R.string.up)
                binding.boatImageView.rotation = 90F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_stern, viewModel.boatNo))

            }

            MainActivity.UPLEFT -> {
                binding.bottomTextView.text = getString(R.string.port)
                binding.topTextView.text = getString(R.string.stbd)
                binding.leftTextView.text = getString(R.string.up)
                binding.rightTextView.text = getString(R.string.down)
                binding.boatImageView.rotation = 270F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_stern, viewModel.boatNo))


            }

            MainActivity.UPRIGHTBACK -> {
                binding.bottomTextView.text = getString(R.string.port)
                binding.topTextView.text = getString(R.string.stbd)
                binding.leftTextView.text = getString(R.string.down)
                binding.rightTextView.text = getString(R.string.up)
                binding.boatImageView.rotation = 90F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_bow, viewModel.boatNo))


            }

            MainActivity.UPLEFTBACK -> {
                binding.bottomTextView.text = getString(R.string.stbd)
                binding.topTextView.text = getString(R.string.port)
                binding.leftTextView.text = getString(R.string.up)
                binding.rightTextView.text = getString(R.string.down)
                binding.boatImageView.rotation = 270F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_bow, viewModel.boatNo))


            }
        }
    }

    private fun navigateFromNonLogging(actionId: Int) {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.nonLoggingFragment) {
            navController.navigate(actionId)
        }
    }


    // every updateinteerval ms, change timer and fields
    private val mUpdateTimeTask: Runnable = object : Runnable {
        override fun run() {

            if (!viewModel.locationAllowed) {
                binding.statusTextview.text = getString(R.string.gps_not_allowed)
            } else {
                if (!viewModel.backgroundLocationAllowed) {
                    binding.statusTextview.text = getString(R.string.backgrnd_gps_not_allowed)
                }
            }


            val uptime = System.currentTimeMillis()
            //If logging, navigate to Logging fragment and don't update view of this one again
            if (viewModel.logging) {
                navigateFromNonLogging(R.id.action_nonLoggingFragment_to_loggingFragment)
            } else {

                screenRefreshHandler!!.postDelayed(this, viewModel.updateInterval.toLong()) // call again after MHANDLER_REFRESH_MS
                if (viewModel.zeroHeelPressed) {
                    if (uptime - viewModel.zeroHeelPressedTime > 3000) {
                        viewModel.zeroHeelPressed = false
                        viewModel.heelOffset -= viewModel.heel - viewModel.extraHeelOffset

                        context?.let {
                            PreferenceManager.getDefaultSharedPreferences(it).edit().apply {
                                putFloat(ViewModelMain.HEEL_OFFSET, viewModel.heelOffset.toFloat())
                                apply()
                            }
                        }
                    }
                }
                if (viewModel.zeroPitchPressed) {
                    if (uptime - viewModel.zeroPitchPressedTime > 3000) {
                        viewModel.zeroPitchPressed = false
                        viewModel.pitchOffset -= viewModel.pitch - viewModel.extraPitchOffset
                        context?.let {
                            PreferenceManager.getDefaultSharedPreferences(it).edit().apply {
                                putFloat(ViewModelMain.PITCH_OFFSET, viewModel.pitchOffset.toFloat())
                                apply()
                            }
                        }
                    }
                }
                if (viewModel.zeroPitchPressed || viewModel.zeroHeelPressed) {
                    if (viewModel.zeroPitchPressed) binding.pitchTextview.text = getString(
                        R.string.in_plus_number,
                        ((MainActivity.ZEROVALUEDELAY + viewModel.zeroPitchPressedTime - uptime) / 1000) + 1
                    )//"In:${((MainActivity.ZEROVALUEDELAY + viewModel.zeroPitchPressedTime - uptime) / 1000) + 1}"
                    if (viewModel.zeroHeelPressed) binding.heelTextview.text = getString(
                        R.string.in_plus_number,
                        ((MainActivity.ZEROVALUEDELAY + viewModel.zeroHeelPressedTime - uptime) / 1000) + 1
                    )//"In:${((MainActivity.ZEROVALUEDELAY + viewModel.zeroHeelPressedTime - uptime) / 1000) + 1}"

                } else {
                    binding.heelTextview.text = viewModel.heel.format(1)
                    binding.pitchTextview.text = viewModel.pitch.format(1)
                    binding.headingTextview.text = "${viewModel.heading.roundToInt()}"
                }
            }
        }
    }


    override fun onLocationChanged(location: Location) {
        locationsAcquired++
        var gpsTime = location.time
        if ((gpsTime > 0) && (gpsTime < 1673000000000L))
            gpsTime += 619315200000L
        viewModel.phoneTimeToGpsOffset = ((System.currentTimeMillis() - gpsTime) / 1000) * 1000
        viewModel.currentLocation = location
        viewModel.currentLocation.time=gpsTime
        val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(gpsTime), ZoneId.of("UTC"))
        val timeString = date.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        binding.gpsTimeTextview.text = "${timeString}UTC"
        if (!hasFirstLocation && locationsAcquired > 1) {
            Log.d(TAG, "Got first location at: $timeString UTC and offset is ${viewModel.phoneTimeToGpsOffset} ")
            hasFirstLocation = true
            if (abs(viewModel.phoneTimeToGpsOffset) > 600000L && !timeOffsetWarningShown) {
                timeOffsetWarningShown = true
                AlertDialog.Builder(requireContext()).setTitle("Phone Time Offseted!")
                    .setMessage("Phone internal time is not matching the GPS time. Might couse some problems while logging. Set your phone time by using the automatic time while your phone is online.")
                    .setPositiveButton("OK") { _, _ -> }
                    .show()
            }
        }
        if (!viewModel.logging) binding.buttonStartLogging.isEnabled = true

        if (hasFirstLocation) {

            if (location.accuracy > 10) {
                binding.statusTextview.text = getString(R.string.gps_signal_medium)
                binding.statusTextview.setTextColor(resources.getColor(R.color.dark_orange, null))
            } else {
                binding.statusTextview.text = getString(R.string.gps_signal_excellent)
                binding.statusTextview.setTextColor(Color.GREEN)
            }
        } else {
            binding.statusTextview.text = getString(R.string.no_gps_signal)
            binding.statusTextview.setTextColor(Color.RED)
        }

        binding.startLineTextView.text= getStartLineString(viewModel.logEventList,location.latitude, location.longitude,location.time)

    }


    override fun onSensorChanged(sensorEvent: SensorEvent) {
        var heel: Double
        var pitch: Double
        val heading: Double

        when (sensorEvent.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                viewModel.mAccelerometerData = sensorEvent.values.clone()
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                viewModel.mMagnetometerData = sensorEvent.values.clone()
                return
            }

            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                viewModel.mGameRotationData = sensorEvent.values.clone()
            }

            else -> return
        }
        val rotationMatrix = FloatArray(9)
        val rotationOK = SensorManager.getRotationMatrix(
            rotationMatrix,
            null, viewModel.mAccelerometerData, viewModel.mMagnetometerData
        )
        val orientationValues = FloatArray(3)
        if (rotationOK) {
            SensorManager.getOrientation(rotationMatrix, orientationValues)
            heading = (180 + Math.toDegrees(orientationValues[0].toDouble())).roundDecimals(1)
            pitch = (Math.toDegrees(orientationValues[1].toDouble())).roundDecimals(4)
            heel = (Math.toDegrees(orientationValues[2].toDouble())).roundDecimals(4)
        } else { // Most chances are that there are no magnet datas
            val gx = viewModel.mAccelerometerData[0] / 9.81f
            val gy = viewModel.mAccelerometerData[1] / 9.81f
            val gz = viewModel.mAccelerometerData[2] / 9.81f
            pitch = -atan(gy / sqrt((gx * gx + gz * gz).toDouble()))
            heel = -atan(gx / sqrt((gy * gy + gz * gz).toDouble()))
            // Log.d(TAG, "Pitch $pitch, roll $heel")
            heading = 0.0 // Impossible to guess
            //get values to be shown at the screen, they update in handler runnable

        }


        val op = OrientationParams(pitch, heel, heading).rotate(viewModel.rotation)

        viewModel.heading = op.heading

        pitch = (op.pitch + viewModel.pitchOffset + viewModel.extraPitchOffset).roundDecimals(2)
        heel = (op.heel + viewModel.heelOffset + viewModel.extraHeelOffset).roundDecimals(2)
        viewModel.counter++

        if (heelList.size > 100) heelList.removeAt(0)
        heelList.add(heel)

        viewModel.heel = heelList.average()

        if (pitchList.size > 100) pitchList.removeAt(0)
        pitchList.add(pitch)

        viewModel.pitch = pitchList.average()


    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }


    private fun hasPermissions(context: Context, permissions: MutableList<String>): Boolean {
        Log.d(TAG, "Checking list of: $permissions")
        return permissions.all {
            val isGranted = ActivityCompat.checkSelfPermission(context, it)
            Log.d(TAG, "Checking: $it, is granted= $isGranted")
            isGranted == PackageManager.PERMISSION_GRANTED
        }

    }

    private fun startLocManager() {
        if (locManager != null) {
            if (requireActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                viewModel.locationAllowed = true
                locManager!!.requestLocationUpdates("gps", 0, 0f, this)
            } else {
                viewModel.locationAllowed = false
            }
        } else {
            Log.d(TAG, "Loc Manager==null")
        }
    }

    override fun onProviderDisabled(provider: String) {
        Log.d(TAG, "On Provider Disabled")
    }

    override fun onProviderEnabled(provider: String) {
        Log.d(TAG, "On Provider Enabled")
    }

    override fun onDismiss(dialog: DialogInterface?) {
        rotateViews()
        fillTextViews()
    }

    private fun fillTextViews() {

        //write data to textviews
        binding.boatNoTextView.text = viewModel.boatNo.toString()
        binding.boatNoTextView.setTextColor(getBoatColor(viewModel.boatNo))
        binding.frequencyTextView.text = getString(R.string.freq, 1000 / viewModel.updateInterval)
        binding.teamTextView.text = viewModel.teamName
        binding.teamTextView.setTextColor(getBoatColor(viewModel.boatNo))

        binding.versionTextView.text = getString(R.string.version, BuildConfig.VERSION_NAME)
    }

    private fun startLogging() {


            saveEvents(viewModel.logEventList, requireContext(),clearEvents=true)
            viewModel.logEventList.clear()

        screenRefreshHandler?.removeCallbacks(mUpdateTimeTask)
        viewModel.serviceIntent = Intent(requireContext(), ForegroundService::class.java)
        viewModel.fillIntent()
        requireContext().bindService(viewModel.serviceIntent, viewModel.connection, AppCompatActivity.BIND_AUTO_CREATE)
        if (!viewModel.foregroundService.isRunning) {
            Log.d(TAG, "Starting new service")
            startForegroundService(requireContext(), viewModel.serviceIntent)
        } else {
            Log.d(TAG, "No Need to Start new service")
        }
        viewModel.foregroundService.logPointList.clear()
        viewModel.foregroundService.locCounter = 0
        viewModel.logging = true
    }




    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

    private var markButtonListener=View.OnClickListener {
        val dialog = MarkDialogFragment()
        val bundle = Bundle()
        // put all values that have some meaning
        bundle.putLong(LoggingFragment.TIMESTAMPKEY, viewModel.currentLocation.time)
        bundle.putDouble(LoggingFragment.LATITUDEKEY, viewModel.currentLocation.latitude)
        bundle.putDouble(LoggingFragment.LONGITUDEKEY, viewModel.currentLocation.longitude)

        dialog.arguments = bundle
        dialog.show(parentFragmentManager, LoggingFragment.MARKDIALOGREQUESTKEY)
    }

    val rotateButtonListener=View.OnClickListener {
        viewModel.rotation++
        if (viewModel.rotation > 7) viewModel.rotation = 0
        with (viewModel.sharedPref.edit()){
            putInt(ViewModelMain.ROTATION, viewModel.rotation)
            apply()
        }
        rotateViews()
    }











}

