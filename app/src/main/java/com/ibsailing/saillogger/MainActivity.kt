package com.ibsailing.saillogger

import android.Manifest
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.ibsailing.saillogger.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {


  //  private lateinit var viewModel.serviceIntent: Intent
    private lateinit var viewModel: ViewModelMain
    private lateinit var binding: ActivityMainBinding





    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        // Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        applySystemBarInsets()
        //ViewModel
        viewModel = ViewModelProvider(this)[ViewModelMain::class.java]


        val isStart=intent.getStringExtra("intentmessage")=="start"
        //Set orientation to portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT


        //Get boat number, offsets, update interval, team name from preferences
        viewModel.sharedPref = PreferenceManager.getDefaultSharedPreferences(this)


       viewModel.loadPrefs()


      //  viewModel.blManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
      //  viewModel.bluetoothAdapter=viewModel.blManager.adapter
      //  viewModel.bluetoothLeScanner = viewModel.bluetoothAdapter.bluetoothLeScanner

        //Hide Title Bar
       supportActionBar?.hide()
        //Location permission

        Log.d(TAG, "Build.VERSION.SDK_INT=${Build.VERSION.SDK_INT}")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        viewModel.pm = getSystemService(Context.POWER_SERVICE ) as PowerManager


        Log.d(TAG,"Density = ${getDeviceDensityString(this)}")

        startApp()
    }


    private fun startApp() {
        Log.d(TAG, "Startapp")
        recoverInterruptedLogs()
        checkBattery()

    }

    private fun applySystemBarInsets() {
        val root = binding.root
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = initialLeft + bars.left,
                top = initialTop + bars.top,
                right = initialRight + bars.right,
                bottom = initialBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    override fun onDestroy() {
        Log.d(TAG, "OnDestroy")
        super.onDestroy()
    }

    override fun onStop() {
        Log.d(TAG, "OnStop")
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "OnStart")

    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "OnResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "OnPause")

    }


    companion object {


        var permissionsNeeded = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)



        const val ZEROVALUEDELAY: Long = 3000
        private const val TAG = "MainActivity"

        const val BOWTOP = 0
        const val BOWRIGHT = 1
        const val BOWBOTTOM = 2
        const val BOWLEFT = 3
        const val UPRIGHT = 4
        const val UPLEFT = 5
        const val UPRIGHTBACK = 6
        const val UPLEFTBACK = 7

    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == NonLoggingFragment.PERMISSION_CODE) {

            Log.d(TAG, "OnrequestPermissionresult")
            Log.d(TAG, "Request Code:$requestCode")
            Log.d(TAG, "permissions:${permissions.contentToString()}")
            Log.d(TAG, "permission results:${grantResults.contentToString()}")


            if (!grantResults.all {
                    it == PackageManager.PERMISSION_GRANTED
                }) {
                Toast.makeText(this, "Activity: Please allow app permissions...", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    }


    fun isIgnoringBatteryOptimizations(): Boolean {
        val pwrm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val name = packageName
        return pwrm.isIgnoringBatteryOptimizations(name)
    }

    private fun checkBattery() {
        if (!isIgnoringBatteryOptimizations() && !viewModel.dontShowBattery) {
            AlertDialog.Builder(this)
                .setTitle("Allow background battery use?")
                .setMessage("SailLogger can keep logging with normal battery settings, but some phones may stop GPS while locked. For long sails, set SailLogger to Unrestricted or Not optimized.")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton("Not Now") { _, _ ->
                    Toast.makeText(this, "Battery optimization may stop GPS logging while the phone is locked.", Toast.LENGTH_LONG).show()
                }
                .setNeutralButton("Don't Show Again") { _, _ ->
                    viewModel.sharedPref.edit().putBoolean(ViewModelMain.DONTSHOWAGAINBATTERY, true).apply()
                    viewModel.savePrefs()
                }
                .show()
        }
    }

    private fun recoverInterruptedLogs() {
        val outputDir = applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir
        try {
            val recoveredLogs = DurableLogWriter.recoverInterruptedLogs(outputDir)
            if (recoveredLogs.isNotEmpty()) {
                Toast.makeText(
                    this,
                    "Recovered ${recoveredLogs.size} interrupted log file(s).",
                    Toast.LENGTH_LONG
                ).show()
                Log.i(TAG, "Recovered interrupted logs: ${recoveredLogs.map { it.recoveredFile.name }}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to recover interrupted logs", e)
            Toast.makeText(this, "SailLogger could not recover an interrupted log.", Toast.LENGTH_LONG).show()
        }
    }

    private fun getDeviceDensityString(context: Context): String? {
        when (context.resources.displayMetrics.densityDpi) {
            DisplayMetrics.DENSITY_LOW -> return "ldpi"
            DisplayMetrics.DENSITY_MEDIUM -> return "mdpi"
            DisplayMetrics.DENSITY_TV, DisplayMetrics.DENSITY_HIGH -> return "hdpi"
            DisplayMetrics.DENSITY_260, DisplayMetrics.DENSITY_280, DisplayMetrics.DENSITY_300, DisplayMetrics.DENSITY_XHIGH -> return "xhdpi"
            DisplayMetrics.DENSITY_340, DisplayMetrics.DENSITY_360, DisplayMetrics.DENSITY_400, DisplayMetrics.DENSITY_420, DisplayMetrics.DENSITY_440, DisplayMetrics.DENSITY_XXHIGH -> return "xxhdpi"
            DisplayMetrics.DENSITY_560, DisplayMetrics.DENSITY_XXXHIGH -> return "xxxhdpi"
        }
        return null
    }
}
