package com.ibsailing.saillogger

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.ibsailing.saillogger.databinding.MarkFragmentLayoutBinding
import kotlin.math.roundToInt

class MarkDialogFragment:DialogFragment(),View.OnClickListener {


    private var timeStamp:Long=0L
    private var latitude:Double=0.0
    private var longitude:Double=0.0

    private lateinit var binding: MarkFragmentLayoutBinding
    private lateinit var viewModel: ViewModelMain


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        timeStamp=requireArguments().getLong(LoggingFragment.TIMESTAMPKEY)
        if(timeStamp==0L){timeStamp=System.currentTimeMillis()}
        latitude=requireArguments().getDouble(LoggingFragment.LATITUDEKEY)
        longitude=requireArguments().getDouble(LoggingFragment.LONGITUDEKEY)

        viewModel = ViewModelProvider(requireActivity())[ViewModelMain::class.java]


        Log.d(TAG,"On created: timestamp: $timeStamp, latitude $latitude, longitude $longitude")

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
       binding= MarkFragmentLayoutBinding.inflate(inflater, container, false)
        Log.d(TAG,"On create view: timestamp: $timeStamp, latitude $latitude, longitude $longitude")

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.portStartButton.setOnClickListener(this)
        binding.stbdStartButton.setOnClickListener(this)
        binding.startTimeButton.setOnClickListener(this)
        binding.eventTextButton.setOnClickListener(this)
        Log.d(TAG,"On view created: timestamp: $timeStamp, latitude $latitude, longitude $longitude")

        writeStartLineData()

        super.onViewCreated(view, savedInstanceState)


    }

    override fun onClick(v: View) {

        when(v.id){

            R.id.portStartButton -> {
                val logEvent=LogEvent()
                logEvent.startTimeStamp=timeStamp
                logEvent.eventType=EventType.PORT_START
                logEvent.eventText=binding.eventTextEditText.text.toString()
                logEvent.latitude=latitude
                logEvent.longitude=longitude
                viewModel.logEventList.add(logEvent)
saveEvents(viewModel.logEventList,requireContext())
                dismiss()
            }

            R.id.stbdStartButton -> {
                val logEvent=LogEvent()
                logEvent.startTimeStamp=timeStamp
                logEvent.eventType=EventType.STARBOARD_START
                logEvent.eventText=binding.eventTextEditText.text.toString()
                logEvent.latitude=latitude
                logEvent.longitude=longitude
                viewModel.logEventList.add(logEvent)
                saveEvents(viewModel.logEventList,requireContext())

                dismiss()
            }



            R.id.startTimeButton -> {
            val numberPicker=NumberPicker(requireContext())
            val maxValue=10
            val minValue=-10
            numberPicker.minValue = 0
            numberPicker.maxValue = maxValue - minValue
            numberPicker.value = 0 - minValue
            numberPicker.setFormatter { index -> (index + minValue).toString() }

            AlertDialog.Builder(requireContext()).setTitle("Minutes to start").setView(numberPicker)
                .setPositiveButton("OK"){_,_ ->
                val logEvent=LogEvent()
                logEvent.startTimeStamp=timeStamp+(numberPicker.value+minValue)*60000
                logEvent.eventType=EventType.START_TIME
                logEvent.eventText=binding.eventTextEditText.text.toString()
                logEvent.latitude=latitude
                logEvent.longitude=longitude
                logEvent.valueName="minutestostart"
                logEvent.value=0f
                viewModel.logEventList.add(logEvent)
                saveEvents(viewModel.logEventList,requireContext())
                dismiss()
                }
                .setNegativeButton("Cancel"){_,_ ->
                    dismiss()
                }.show()
            }
            R.id.event_text_button ->{
                val logEvent=LogEvent()
                logEvent.startTimeStamp=timeStamp
                logEvent.eventType=EventType.CUSTOM_TEXT
                logEvent.eventText=binding.eventTextEditText.text.toString()
                logEvent.latitude=latitude
                logEvent.longitude=longitude
                viewModel.logEventList.add(logEvent)
                saveEvents(viewModel.logEventList,requireContext())
                dismiss()

            }
        }

    }

    companion object{
        const val TAG="MarkDialogFragment"

    }


    override fun onStart() {
        super.onStart()
        val dialog=dialog
        if (dialog != null) {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.MATCH_PARENT
            dialog.window?.setLayout(width, height)
        }
    }


    fun writeStartLineData(){

       binding.startLineInfoTextView.text= getStartLineString(viewModel.logEventList,latitude,longitude,0)
        }


}