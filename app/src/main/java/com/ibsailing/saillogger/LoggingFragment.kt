package com.ibsailing.saillogger

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.ibsailing.saillogger.databinding.FragmentLoggingBinding
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt


class LoggingFragment : Fragment(), SensorEventListener, LocationListener {


    private var mService:ForegroundService?=null
    private lateinit var viewModel:ViewModelMain

private var locationsAcquired=0
    private var lastLocationTime=0L

    private var mSensorAccelerometer: Sensor?=null
    private var mSensorMagnetometer: Sensor?=null
    private var mSensorManager: SensorManager?=null

    private var screenRefreshHandler: Handler? = null

    private var locManager: LocationManager? = null

    private var isRefreshScreen=false

    private lateinit var binding:FragmentLoggingBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        viewModel = ViewModelProvider(requireActivity())[ViewModelMain::class.java]

        // handler
        screenRefreshHandler = Handler(Looper.getMainLooper())

        // Get accelerometer and magnetometer sensors from the sensor manager.
        // The getDefaultSensor() method returns null if the sensor
        // is not available on the device.
        mSensorManager = requireActivity().getSystemService(
            AppCompatActivity.SENSOR_SERVICE
        ) as SensorManager
        mSensorAccelerometer = mSensorManager!!.getDefaultSensor(
            Sensor.TYPE_ACCELEROMETER
        )
        mSensorMagnetometer = mSensorManager!!.getDefaultSensor(
            Sensor.TYPE_MAGNETIC_FIELD
        )
        locManager = requireActivity().getSystemService(AppCompatActivity.LOCATION_SERVICE) as LocationManager
        viewModel.currentLocation = Location("gps")

        // Bind to LocalService
        Log.d(TAG,"On Create")





    }



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding= FragmentLoggingBinding.inflate(inflater, container, false)
        // Inflate the layout for this fragment
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.loggingStopButton.setOnClickListener{
            binding.loggingStopButton.isEnabled = false
            binding.loggingUnlockButton.isEnabled = true
            stopLogging()
            findNavController().navigate(R.id.action_loggingFragment_to_nonLoggingFragment)
        }
        binding.loggingUnlockButton.setOnClickListener{
            binding.loggingUnlockButton.isEnabled = false
            binding.loggingStopButton.isEnabled = true
        }
        binding.loggingMarkButton.setOnClickListener(loggingMarkListener)

        if (viewModel.hasForegroundService()) {
            setService(viewModel.foregroundService)
        } else {
            viewModel.logging = false
            findNavController().navigateUp()
            return
        }

        rotateViews()

        binding.teamNameTextView.text=viewModel.teamName
        binding.teamNameTextView.setTextColor(getBoatColor(viewModel.boatNo))

        binding.boatNoTextView.text=viewModel.boatNo.toString()
        binding.boatNoTextView.setTextColor(getBoatColor(viewModel.boatNo))

    }

    companion object {

        private const val TAG="LoggingFragment"



        const val LATITUDEKEY="LATITUDEKEY"
        const val LONGITUDEKEY="LONGITUDEKEY"
        const val TIMESTAMPKEY="TIMESTAMPKEY"
        const val MARKDIALOGREQUESTKEY="MARKDIALOGREQUESTKEY"

    }


  private fun setService(foregroundService: ForegroundService){
      mService=foregroundService
  }







    override fun onSensorChanged(sensorEvent: SensorEvent) {
        when (sensorEvent.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                viewModel.mAccelerometerData = sensorEvent.values.clone()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> viewModel.mMagnetometerData = sensorEvent.values.clone()
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
        }
        //get values to be shown at the screen, they update in handler runnable

        viewModel.heading = (180 + Math.toDegrees(orientationValues[0].toDouble())).roundDecimals(1)
        viewModel.pitch = (Math.toDegrees(orientationValues[1].toDouble())).roundDecimals(4)
        viewModel.heel = (Math.toDegrees(orientationValues[2].toDouble())).roundDecimals(4)

        val op= OrientationParams(viewModel.pitch, viewModel.heel, viewModel.heading).rotate(viewModel.rotation)
        viewModel.pitch=op.pitch
        viewModel.heel=op.heel
        viewModel.heading=op.heading

        viewModel.pitch = (viewModel.pitch+viewModel.pitchOffset+viewModel.extraPitchOffset).roundDecimals(2)
        viewModel.heel = (viewModel.heel+viewModel.heelOffset+viewModel.extraHeelOffset).roundDecimals(2)
        viewModel.counter++
    }


    override fun onAccuracyChanged(sensor: Sensor?, p1: Int) {
    }



    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

    override fun onLocationChanged(location: Location) {
        locationsAcquired++
        var gpsTime = location.time
        if ((gpsTime > 0) && (gpsTime < 1673000000000L))
            gpsTime += 619315200000L
        location.time=gpsTime
        val speed=(1.94384*location.speed).roundDecimals(1)
        val cog=location.bearing.roundToInt()
        binding.sogNumberTextView.text=("$speed")
        binding.cogTextviewLogging.text=("$cog")
        val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(gpsTime), ZoneId.of("UTC"))
        val timeString = date.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        binding.loggingGpsTimeTextview.text = "${timeString}UTC"



        if(locationsAcquired<2 || System.currentTimeMillis()-lastLocationTime>10000){
            binding.gpsTextView.text=getString(R.string.no_gps_signal)
            binding.loggingActiveTextView.setTextColor(Color.RED)
            binding.gpsTextView.setTextColor(Color.RED)
        }else {
            if (locationsAcquired >= 2 && System.currentTimeMillis() - lastLocationTime < 10000 && location.accuracy < 10) {
                binding.gpsTextView.text = getString(R.string.gps_signal_excellent)
                binding.loggingActiveTextView.setTextColor(Color.GREEN)
                binding.gpsTextView.setTextColor(Color.GREEN)

            } else {
                binding.gpsTextView.text = getString(R.string.gps_signal_medium)
                binding.loggingActiveTextView.setTextColor(resources.getColor(R.color.dark_orange, null))
                binding.gpsTextView.setTextColor(resources.getColor(R.color.dark_orange, null))

            }
        }

        locationsAcquired++
        lastLocationTime=System.currentTimeMillis()
        binding.startLineTextView.text= getStartLineString(viewModel.logEventList,location.latitude, location.longitude,location.time)
    }

    // every updateinteerval ms, change timer and fields
    private val mUpdateTimeTask: Runnable = object : Runnable {
        override fun run() {
            if(isRefreshScreen) {
                screenRefreshHandler!!.postDelayed(this, viewModel.updateInterval.toLong()) // call again after MHANDLER_REFRESH_MS
            }

            if (viewModel.logging) {
                    val service = mService
                    val logPointList = service?.logPointList?:ArrayList()
                    val totalLoggedPoints = service?.loggedPointCounter ?: logPointList.size.toLong()
                    val logStartTimestamp = service?.logStartTimestamp ?: logPointList.firstOrNull()?.timeStamp ?: 0L
                    val lastLogTimestamp = service?.lastLogTimestamp ?: logPointList.lastOrNull()?.timeStamp ?: 0L
                    if (logPointList.isNotEmpty()) {
                        val logPoint = logPointList.last()
                        val date = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(lastLogTimestamp-logStartTimestamp), ZoneId.ofOffset("",
                                ZoneOffset.UTC))
                        val durationString = date.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        binding.maxSpeedTextview.text =((mService?.maxSpeed?: (0F))* 1.94384).format(2)
                        mService?.let { binding.heelTextviewLogging.visibleIf(it.isLoggingHeel) }
                        mService?.let { binding.pitchTextviewLogging.visibleIf(it.isLoggingPitch) }
                        mService?.let { binding.headingTextviewLogging.visibleIf(it.isLoggingHeading) }



                        logPoint.heel?.let{binding.heelTextviewLogging.text=it.format(1)}
                        logPoint.pitch?.let{binding.pitchTextviewLogging.text=it.format(1)}
                        binding.headingTextviewLogging.text="${viewModel.heading.roundToInt()}"

                        binding.loggedPointsTextview.text="$totalLoggedPoints"
                        binding.loggedLocationsTextview.text="${mService?.locCounter}"
                        binding.loggedTimeTextview.text=durationString
                        binding.eventsNumberTextView.text= viewModel.logEventList.size.toString()
                    }
                }

        }
    }


    override fun onProviderDisabled(provider: String) {Log.d(TAG,"On Provider Disabled")}
    override fun onProviderEnabled(provider: String) {Log.d(TAG,"On Provider Enabled")}
   // override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {Log.d(TAG,"On Status Changed")}



    private fun rotateViews() {




        when (viewModel.rotation) {
            MainActivity.BOWBOTTOM -> {
                binding.bottomTextView.text = getString(R.string.bow)
                binding.topTextView.text = getString(R.string.stern)
                binding.leftTextView.text = getString(R.string.stbd)
                binding.rightTextView.text = getString(R.string.port)
                binding.boatImageView.rotation=270F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_top, viewModel.boatNo))

            }
            MainActivity.BOWTOP -> {
                binding.bottomTextView.text = getString(R.string.stern)
                binding.topTextView.text = getString(R.string.bow)
                binding.leftTextView.text = getString(R.string.port)
                binding.rightTextView.text = getString(R.string.stbd)
                binding.boatImageView.rotation=90F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_top, viewModel.boatNo))

            }
            MainActivity.BOWLEFT -> {
                binding.bottomTextView.text = getString(R.string.port)
                binding.topTextView.text = getString(R.string.stbd)
                binding.leftTextView.text = getString(R.string.bow)
                binding.rightTextView.text = getString(R.string.stern)
                binding.boatImageView.rotation=0F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_top, viewModel.boatNo))


            }
            MainActivity.BOWRIGHT -> {
                binding.bottomTextView.text = getString(R.string.stbd)
                binding.topTextView.text = getString(R.string.port)
                binding.leftTextView.text = getString(R.string.stern)
                binding.rightTextView.text = getString(R.string.bow)
                binding.boatImageView.rotation=180F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_top, viewModel.boatNo))


            }
            MainActivity.UPRIGHT -> {
                binding.bottomTextView.text = getString(R.string.stbd)
                binding.topTextView.text = getString(R.string.port)
                binding.leftTextView.text = getString(R.string.down)
                binding.rightTextView.text = getString(R.string.up)
                binding.boatImageView.rotation=90F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_stern, viewModel.boatNo))


            }
            MainActivity.UPLEFT -> {
                binding.bottomTextView.text = getString(R.string.port)
                binding.topTextView.text = getString(R.string.stbd)
                binding.leftTextView.text = getString(R.string.up)
                binding.rightTextView.text = getString(R.string.down)
                binding.boatImageView.rotation=270F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_stern, viewModel.boatNo))


            }
            MainActivity.UPRIGHTBACK -> {
                binding.bottomTextView.text = getString(R.string.port)
                binding.topTextView.text = getString(R.string.stbd)
                binding.leftTextView.text = getString(R.string.down)
                binding.rightTextView.text = getString(R.string.up)
                binding.boatImageView.rotation=90F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_bow, viewModel.boatNo))


            }
            MainActivity.UPLEFTBACK -> {
                binding.bottomTextView.text = getString(R.string.stbd)
                binding.topTextView.text = getString(R.string.port)
                binding.leftTextView.text = getString(R.string.up)
                binding.rightTextView.text = getString(R.string.down)
                binding.boatImageView.rotation=270F
                binding.boatImageView.setImageDrawable(requireContext().getBoatDrawable(R.drawable.boat_bow, viewModel.boatNo))

            }
        }
    }


    override fun onPause() {
        super.onPause()
        locManager?.removeUpdates(this)
        mSensorManager?.unregisterListener(this)
        isRefreshScreen=false
    }




    override fun onResume() {
        super.onResume()

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

        if (locManager != null) {
            if (requireActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                locManager!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
            }
        }
        isRefreshScreen=true
        screenRefreshHandler!!.postDelayed(mUpdateTimeTask, 300)



    }

    override fun onStop() {
        Log.d(TAG, "OnStop")
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "OnStart")

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "OnAttach")

    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OnDestroy")
    }

    override fun onDetach() {
        super.onDetach()
        Log.d(TAG, "OnDetach")

    }

    private fun stopLogging() {
        Log.d(TAG, "Stop Logging")
        if (viewModel.mBound && viewModel.hasForegroundService()) {
            //viewModel.foregroundService.saveCSV(incomplete = false)
            viewModel.foregroundService.addPointsToFile()
            requireContext().unbindService(viewModel.connection)
            viewModel.foregroundService.stopService()
        }
        viewModel.logging = false
    }


    val loggingMarkListener=View.OnClickListener {
        val dialog = MarkDialogFragment()
        val bundle = Bundle()
        // put all values that have some meaning
        mService?.currentLocation?.time?.let { bundle.putLong(TIMESTAMPKEY, it) }
        mService?.currentLocation?.latitude?.let { bundle.putDouble(LATITUDEKEY, it) }
        mService?.currentLocation?.longitude?.let { bundle.putDouble(LONGITUDEKEY, it) }

        dialog.arguments = bundle
        dialog.show(parentFragmentManager, MARKDIALOGREQUESTKEY)

    }
}
