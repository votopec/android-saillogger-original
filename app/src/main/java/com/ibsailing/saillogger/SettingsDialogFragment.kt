package com.ibsailing.saillogger

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
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

    private lateinit var viewModel:ViewModelMain
    private lateinit var binding:FragmentSettingsBinding
    private var updateInterval=1000
    private var boatNo=1
    private var extraPitch=0.0
    private var extraHeel=0.0
    private var eventsPerQR=10

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d("permissions", "${it.key} = ${it.value}")
            }
        }

    private var requestBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            //granted
        }else{
            //deny
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        viewModel = ViewModelProvider(requireActivity())[ViewModelMain::class.java]

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root        // Inflate the layout for this fragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.extraPitchTextView.text = viewModel.extraPitchOffset.roundDecimals(2).toString()
        binding.extraHeelTextView.text=viewModel.extraHeelOffset.roundDecimals(2).toString()
        binding.teamTextView.text=viewModel.teamName
        updateInterval=viewModel.updateInterval
        boatNo=viewModel.boatNo
        binding.boatnoColor.setBackgroundColor(getBoatColor(viewModel.boatNo))
        binding.boatnoTextView.text=boatNo.toString()
        extraPitch=viewModel.extraPitchOffset
        extraHeel=viewModel.extraHeelOffset



        binding.qrSizeTextView.text=viewModel.eventsPerQr.toString()
        binding.logHeelSwitch.isChecked=viewModel.isLoggingHeel
        binding.logPitchSwitch.isChecked=viewModel.isLoggingPitch
        binding.logAltitudeSwitch.isChecked=viewModel.isLoggingAltitude
        binding.logMagnetSwitch.isChecked=viewModel.isLoggingMagnetometer
        binding.logHeadingSwitch.isChecked=viewModel.isLoggingHeading


      fillTextViews()

        binding.logCyclopsSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.isLoggingCyclops=isChecked
            if(viewModel.isLoggingCyclops){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Log.d(TAG,"Requesting multiple permissions")
                    requestMultiplePermissions.launch(arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT))
                }
                else{
                    Log.d(TAG,"Requesting multiple permissions lower than android S")
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    requestBluetooth.launch(enableBtIntent)
                }


            }
        }

        binding.extraPitchButton.setOnClickListener {
            val numberInputView= NumberInputView(requireContext(), maxDigits = 15)
            numberInputView.setTitleText("Extra Pitch Offset")

            AlertDialog.Builder(requireContext())
                .setView(numberInputView)
                .setPositiveButton("OK"){
                    _,_ ->
                    val value=numberInputView.text.toDoubleOrNull()
                    if(value!=null)extraPitch=value
                    binding.extraPitchTextView.text=extraPitch.format(2)
                }
                .setNegativeButton("Cancel"){_,_ ->}
                .show()
        }

        binding.extraHeelButton.setOnClickListener {

            val numberInputView= NumberInputView(requireContext(), maxDigits = 15)
            numberInputView.setTitleText("Extra Heel Offset")

            AlertDialog.Builder(requireContext())
                .setView(numberInputView)
                .setPositiveButton("OK"){
                        _,_ ->
                    val value=numberInputView.text.toDoubleOrNull()
                    if(value!=null)extraHeel=value
                    binding.extraHeelTextView.text=extraHeel.format(2)
                }
                .setNegativeButton("Cancel",null)
                .show()
        }

        binding.qrSizeButton.setOnClickListener {

            val numberInputView= NumberInputView(requireContext(),isDecimal = false,isNegative = false, maxDigits = 2)
            numberInputView.setTitleText("Events per QR code")

            AlertDialog.Builder(requireContext())
                .setView(numberInputView)
                .setPositiveButton("OK"){
                        _,_ ->
                    val value=numberInputView.text.toIntOrNull()
                    if(value!=null)eventsPerQR=value
                    binding.qrSizeTextView.text=eventsPerQR.toString()
                }
                .setNegativeButton("Cancel",null)
                .show()
        }

        binding.okSettingsButton.setOnClickListener{

            viewModel.extraPitchOffset=extraPitch
            viewModel.extraHeelOffset=extraHeel
            viewModel.eventsPerQr=eventsPerQR


            viewModel.teamName = binding.teamTextView.text.toString()
            viewModel.updateInterval=updateInterval
            viewModel.boatNo=boatNo
            viewModel.isLoggingHeel=binding.logHeelSwitch.isChecked
            viewModel.isLoggingPitch=binding.logPitchSwitch.isChecked
            viewModel.isLoggingHeading=binding.logHeadingSwitch.isChecked
            viewModel.isLoggingAltitude=binding.logAltitudeSwitch.isChecked
            viewModel.isLoggingMagnetometer=binding.logMagnetSwitch.isChecked

            viewModel.savePrefs()
            dismiss()
        }

        binding.resetOffsetsButton.setOnClickListener {
            viewModel.pitchOffset=0.0
            viewModel.heelOffset=0.0
            fillTextViews()
        }

        binding.cancelSettingsButton.setOnClickListener {
            dismiss()
        }

        binding.teamButton.setOnClickListener {


            val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Team Name")
// Set up the input
            val input = EditText(requireContext())
            input.hint = "Team Name"
            input.setText(viewModel.teamName)
            input.inputType = InputType.TYPE_CLASS_TEXT
            builder.setView(input)

// Set up the buttons
            builder.setPositiveButton("OK"
            ) { _, _ ->
                binding.teamTextView.text=input.text.toString()
            }
            builder.setNegativeButton("Cancel") { _,_ -> }

            builder.show()

        }

        binding.frequancyButton.setOnClickListener {

            when (updateInterval) {
                200 -> updateInterval=500
                500 -> updateInterval=1000
                1000 -> updateInterval=200
            }

fillTextViews()
        }

        binding.boatNoButton.setOnClickListener {

            val linearLayout= LinearLayout(requireContext())
            linearLayout.orientation=LinearLayout.VERTICAL
            val colorView=View(requireContext())
            colorView.layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,100)
            colorView.setBackgroundColor(getBoatColor(viewModel.boatNo))
            colorView.setPadding(30,10,30,10)

            val numberPicker= NumberPicker(context)
            numberPicker.minValue=1
            numberPicker.value=boatNo
            numberPicker.maxValue=100
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
                .setNegativeButton("Cancel") { _, _ ->
                }.show()

        }

    }

    companion object {

const val TAG="SettingsDialogFragment"

    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (parentFragment is DialogInterface.OnDismissListener) {
            (parentFragment as DialogInterface.OnDismissListener?)!!.onDismiss(dialog)
        }
    }

    private fun fillTextViews(){
        binding.pitchOffsetTextView.text=getString(R.string.pitch_with_number,viewModel.pitchOffset.format(2))
        binding.heelOffsetTextView.text=getString(R.string.heel_with_number,viewModel.heelOffset.format(2))
        binding.extraPitchTextView.text=extraPitch.format(2)
        binding.extraHeelTextView.text=extraHeel.format(2)
        binding.frequencyTextView.text = getString(R.string.num_plus_s,1000/updateInterval)

    }
}