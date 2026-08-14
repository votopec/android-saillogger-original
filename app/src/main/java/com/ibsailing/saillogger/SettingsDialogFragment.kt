package com.ibsailing.saillogger

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.ibsailing.saillogger.databinding.FragmentSettingsBinding


class SettingsDialogFragment : DialogFragment() {

    private lateinit var viewModel: ViewModelMain
    private lateinit var binding: FragmentSettingsBinding
    private var updateInterval = 1000
    private var boatNo = 1
    private var extraPitch = 0.0
    private var extraHeel = 0.0
    private var eventsPerQR = 10
    private var refreshHandler: Handler? = null
    private var savedZeroMessageUntil = 0L

    private val refreshTask: Runnable = object : Runnable {
        override fun run() {
            refreshZeroingState()
            refreshSensorText()
            refreshTextViews()
            refreshHandler?.postDelayed(this, SETTINGS_REFRESH_MS)
        }
    }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d("permissions", "${it.key} = ${it.value}")
            }
        }

    private var requestBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            // granted
        } else {
            // denied
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[ViewModelMain::class.java]
        refreshHandler = Handler(Looper.getMainLooper())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateInterval = viewModel.updateInterval
        boatNo = viewModel.boatNo
        extraPitch = viewModel.extraPitchOffset
        extraHeel = viewModel.extraHeelOffset
        eventsPerQR = viewModel.eventsPerQr

        binding.teamTextView.text = viewModel.teamName
        binding.boatnoColor.setBackgroundColor(getBoatColor(viewModel.boatNo))
        binding.boatnoTextView.text = boatNo.toString()
        binding.qrSizeTextView.text = viewModel.eventsPerQr.toString()
        binding.logHeelSwitch.isChecked = viewModel.isLoggingHeel
        binding.logPitchSwitch.isChecked = viewModel.isLoggingPitch
        binding.logAltitudeSwitch.isChecked = viewModel.isLoggingAltitude
        binding.logMagnetSwitch.isChecked = viewModel.isLoggingMagnetometer
        binding.logHeadingSwitch.isChecked = viewModel.isLoggingHeading
        binding.logCyclopsSwitch.isChecked = viewModel.isLoggingCyclops

        setupTabs()
        setupSettingActions()
        setupZeroingActions()
        refreshTextViews()
        refreshSensorText()
        refreshZeroingState()
        startRefreshing()
    }

    override fun onPause() {
        super.onPause()
        stopRefreshing()
        cancelPendingZeroing()
    }

    override fun onResume() {
        super.onResume()
        startRefreshing()
    }

    override fun onDestroyView() {
        stopRefreshing()
        super.onDestroyView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        cancelPendingZeroing()
        super.onDismiss(dialog)
        if (parentFragment is DialogInterface.OnDismissListener) {
            (parentFragment as DialogInterface.OnDismissListener?)!!.onDismiss(dialog)
        }
    }

    private fun setupTabs() {
        binding.settingsBoatTabButton.setOnClickListener { showSettingsPanel(SettingsPanel.BOAT) }
        binding.settingsSensorsTabButton.setOnClickListener { showSettingsPanel(SettingsPanel.SENSORS) }
        binding.settingsSharingTabButton.setOnClickListener { showSettingsPanel(SettingsPanel.SHARING) }
        showSettingsPanel(SettingsPanel.BOAT)
    }

    private fun setupSettingActions() {
        binding.logCyclopsSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.isLoggingCyclops = isChecked
            if (viewModel.isLoggingCyclops) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Log.d(TAG, "Requesting multiple permissions")
                    requestMultiplePermissions.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                    )
                } else {
                    Log.d(TAG, "Requesting bluetooth enable")
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    requestBluetooth.launch(enableBtIntent)
                }
            }
        }

        binding.extraPitchButton.setOnClickListener {
            val numberInputView = NumberInputView(requireContext(), maxDigits = 15)
            numberInputView.setTitleText("Extra Pitch Offset")

            AlertDialog.Builder(requireContext())
                .setView(numberInputView)
                .setPositiveButton("OK") { _, _ ->
                    val value = numberInputView.text.toDoubleOrNull()
                    if (value != null) extraPitch = value
                    refreshTextViews()
                }
                .setNegativeButton("Cancel") { _, _ -> }
                .show()
        }

        binding.extraHeelButton.setOnClickListener {
            val numberInputView = NumberInputView(requireContext(), maxDigits = 15)
            numberInputView.setTitleText("Extra Heel Offset")

            AlertDialog.Builder(requireContext())
                .setView(numberInputView)
                .setPositiveButton("OK") { _, _ ->
                    val value = numberInputView.text.toDoubleOrNull()
                    if (value != null) extraHeel = value
                    refreshTextViews()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.qrSizeButton.setOnClickListener {
            val numberInputView = NumberInputView(
                requireContext(),
                isDecimal = false,
                isNegative = false,
                maxDigits = 2
            )
            numberInputView.setTitleText("Events per QR code")

            AlertDialog.Builder(requireContext())
                .setView(numberInputView)
                .setPositiveButton("OK") { _, _ ->
                    val value = numberInputView.text.toIntOrNull()
                    if (value != null) eventsPerQR = value
                    refreshTextViews()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.okSettingsButton.setOnClickListener {
            viewModel.extraPitchOffset = extraPitch
            viewModel.extraHeelOffset = extraHeel
            viewModel.eventsPerQr = eventsPerQR

            viewModel.teamName = binding.teamTextView.text.toString()
            viewModel.updateInterval = updateInterval
            viewModel.boatNo = boatNo
            viewModel.isLoggingHeel = binding.logHeelSwitch.isChecked
            viewModel.isLoggingPitch = binding.logPitchSwitch.isChecked
            viewModel.isLoggingHeading = binding.logHeadingSwitch.isChecked
            viewModel.isLoggingAltitude = binding.logAltitudeSwitch.isChecked
            viewModel.isLoggingMagnetometer = binding.logMagnetSwitch.isChecked

            viewModel.savePrefs()
            dismiss()
        }

        binding.cancelSettingsButton.setOnClickListener {
            dismiss()
        }

        binding.teamButton.setOnClickListener {
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Team Name")
            val input = EditText(requireContext())
            input.hint = "Team Name"
            input.setText(viewModel.teamName)
            input.inputType = InputType.TYPE_CLASS_TEXT
            builder.setView(input)
            builder.setPositiveButton("OK") { _, _ ->
                binding.teamTextView.text = input.text.toString()
            }
            builder.setNegativeButton("Cancel") { _, _ -> }
            builder.show()
        }

        binding.frequancyButton.setOnClickListener {
            updateInterval = when (updateInterval) {
                200 -> 500
                500 -> 1000
                else -> 200
            }
            refreshTextViews()
        }

        binding.boatNoButton.setOnClickListener {
            val linearLayout = LinearLayout(requireContext())
            linearLayout.orientation = LinearLayout.VERTICAL
            val colorView = View(requireContext())
            colorView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                100
            )
            colorView.setBackgroundColor(getBoatColor(viewModel.boatNo))
            colorView.setPadding(30, 10, 30, 10)

            val numberPicker = NumberPicker(context)
            numberPicker.minValue = 1
            numberPicker.value = boatNo
            numberPicker.maxValue = 100
            numberPicker.setOnValueChangedListener { _, _, newVal ->
                colorView.setBackgroundColor(getBoatColor(newVal))
            }

            linearLayout.addView(colorView)
            linearLayout.addView(numberPicker)

            AlertDialog.Builder(requireContext()).setView(linearLayout)
                .setTitle("Edit Boat Number")
                .setMessage("Boat Color:")
                .setPositiveButton("OK") { _, _ ->
                    boatNo = numberPicker.value
                    binding.boatnoTextView.text = "$boatNo"
                    binding.boatnoColor.setBackgroundColor(getBoatColor(boatNo))
                }
                .setNegativeButton("Cancel") { _, _ -> }
                .show()
        }
    }

    private fun setupZeroingActions() {
        binding.zeroHeelSettingsButton.setOnClickListener {
            viewModel.requestZeroHeel()
            refreshZeroingState()
        }
        binding.zeroPitchSettingsButton.setOnClickListener {
            viewModel.requestZeroPitch()
            refreshZeroingState()
        }
        binding.zeroBothSettingsButton.setOnClickListener {
            viewModel.requestZeroBoth()
            refreshZeroingState()
        }
        binding.resetOffsetsButton.setOnClickListener {
            viewModel.resetZeroOffsets()
            savedZeroMessageUntil = System.currentTimeMillis() + ZERO_SAVED_MESSAGE_MS
            refreshTextViews()
            refreshZeroingState()
        }
    }

    private fun refreshZeroingState() {
        val now = System.currentTimeMillis()
        var zeroSaved = false

        if (viewModel.zeroHeelPressed && now - viewModel.zeroHeelPressedTime >= MainActivity.ZEROVALUEDELAY) {
            viewModel.completeZeroHeel()
            zeroSaved = true
        }
        if (viewModel.zeroPitchPressed && now - viewModel.zeroPitchPressedTime >= MainActivity.ZEROVALUEDELAY) {
            viewModel.completeZeroPitch()
            zeroSaved = true
        }
        if (zeroSaved) {
            savedZeroMessageUntil = now + ZERO_SAVED_MESSAGE_MS
        }

        binding.zeroHeelSettingsButton.isEnabled = !viewModel.zeroHeelPressed
        binding.zeroPitchSettingsButton.isEnabled = !viewModel.zeroPitchPressed
        binding.zeroBothSettingsButton.isEnabled = !viewModel.zeroHeelPressed && !viewModel.zeroPitchPressed

        binding.zeroCountdownTextView.text = when {
            viewModel.zeroHeelPressed && viewModel.zeroPitchPressed -> getString(
                R.string.settings_zero_countdown,
                minOf(viewModel.secondsUntilZeroHeel(now), viewModel.secondsUntilZeroPitch(now))
            )
            viewModel.zeroHeelPressed -> getString(
                R.string.settings_zero_countdown,
                viewModel.secondsUntilZeroHeel(now)
            )
            viewModel.zeroPitchPressed -> getString(
                R.string.settings_zero_countdown,
                viewModel.secondsUntilZeroPitch(now)
            )
            now < savedZeroMessageUntil -> getString(R.string.settings_zero_saved)
            else -> getString(R.string.settings_zero_ready)
        }
    }

    private fun refreshSensorText() {
        binding.liveHeelTextView.text = viewModel.heel.format(1)
        binding.livePitchTextView.text = viewModel.pitch.format(1)
    }

    private fun refreshTextViews() {
        binding.pitchOffsetTextView.text = getString(R.string.pitch_with_number, viewModel.pitchOffset.format(2))
        binding.heelOffsetTextView.text = getString(R.string.heel_with_number, viewModel.heelOffset.format(2))
        binding.extraPitchTextView.text = extraPitch.format(2)
        binding.extraHeelTextView.text = extraHeel.format(2)
        binding.frequencyTextView.text = getString(R.string.num_plus_s, 1000 / updateInterval)
        binding.qrSizeTextView.text = eventsPerQR.toString()
    }

    private fun showSettingsPanel(panel: SettingsPanel) {
        binding.settingsBoatPanel.visibility = if (panel == SettingsPanel.BOAT) View.VISIBLE else View.GONE
        binding.settingsSensorsPanel.visibility = if (panel == SettingsPanel.SENSORS) View.VISIBLE else View.GONE
        binding.settingsSharingPanel.visibility = if (panel == SettingsPanel.SHARING) View.VISIBLE else View.GONE
        updateTabButton(binding.settingsBoatTabButton, panel == SettingsPanel.BOAT)
        updateTabButton(binding.settingsSensorsTabButton, panel == SettingsPanel.SENSORS)
        updateTabButton(binding.settingsSharingTabButton, panel == SettingsPanel.SHARING)
    }

    private fun updateTabButton(button: android.widget.Button, isSelected: Boolean) {
        val backgroundColor = if (isSelected) R.color.app_primary else R.color.app_surface_alt
        val textColor = if (isSelected) R.color.white else R.color.app_text_primary
        button.backgroundTintList = ColorStateList.valueOf(resources.getColor(backgroundColor, null))
        button.setTextColor(resources.getColor(textColor, null))
    }

    private fun startRefreshing() {
        refreshHandler?.removeCallbacks(refreshTask)
        refreshHandler?.post(refreshTask)
    }

    private fun stopRefreshing() {
        refreshHandler?.removeCallbacks(refreshTask)
    }

    private fun cancelPendingZeroing() {
        viewModel.zeroHeelPressed = false
        viewModel.zeroPitchPressed = false
    }

    private enum class SettingsPanel {
        BOAT,
        SENSORS,
        SHARING
    }

    companion object {
        const val TAG = "SettingsDialogFragment"
        private const val SETTINGS_REFRESH_MS = 250L
        private const val ZERO_SAVED_MESSAGE_MS = 2000L
    }
}
