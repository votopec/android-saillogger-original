package com.ibsailing.saillogger


import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_DEL
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import com.ibsailing.saillogger.databinding.NumberInputLayoutBinding


class NumberInputView: FrameLayout {

    lateinit var binding: NumberInputLayoutBinding
    private var isDecimalAllowed=false
    private var isNegativeAllowed=true
    private var maxDigits=99


    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initView()
    }
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0) {
        initView()
    }

    constructor(context: Context) : super(context){
        initView()}

    constructor(context:Context,isDecimal:Boolean=true,isNegative: Boolean=true,maxDigits:Int=99):super(context){
        initView()
        setIsDecimal(isDecimal)
        setIsNegative(isNegative)
        setMaxDigits(maxDigits)
    }

    private fun initView() {
        binding= NumberInputLayoutBinding.inflate(LayoutInflater.from(context), this,true)
        Log.d(TAG,"InitView")
        binding.button0.setOnClickListener(numberOnClick)
        binding.button1.setOnClickListener(numberOnClick)
        binding.button2.setOnClickListener(numberOnClick)
        binding.button3.setOnClickListener(numberOnClick)
        binding.button4.setOnClickListener(numberOnClick)
        binding.button5.setOnClickListener(numberOnClick)
        binding.button6.setOnClickListener(numberOnClick)
        binding.button7.setOnClickListener(numberOnClick)
        binding.button8.setOnClickListener(numberOnClick)
        binding.button9.setOnClickListener(numberOnClick)
        binding.buttonNumericDecimal.setOnClickListener(numberOnClick)
        binding.buttonMinus.setOnClickListener(numberOnClick)



        binding.buttonNumericDelete.setOnClickListener {
            deleteLast()
        }

        binding.buttonNumericClear.setOnClickListener {
            binding.inputNumberTextView.text=""
        }

    }

    fun setTitleText(text:String){
        binding.titleTextView.text=text
        binding.titleTextView.visibility=if(text.isBlank()){View.GONE}else{View.VISIBLE}
    }

    fun setTitleTextSize(unit:Int,size:Float){
        binding.titleTextView.setTextSize(unit,size)
    }

    fun setHintText(text:String){
        binding.inputNumberTextView.hint=text
    }

    fun setResultText(text:String){
        binding.inputNumberTextView.text=text
    }

    fun setIsDecimal(isDecimal:Boolean){
        isDecimalAllowed=isDecimal
        if(isDecimal){
            binding.buttonNumericDecimal.visibility= View.VISIBLE
        }else{
            binding.buttonNumericDecimal.visibility= View.INVISIBLE
        }
    }

    fun setIsNegative(isNegative:Boolean){
        isNegativeAllowed=isNegative
        if(isNegative){
            binding.buttonMinus.visibility= View.VISIBLE
        }else{
            binding.buttonMinus.visibility= View.INVISIBLE
        }
    }



    companion object{
        private const val TAG = "NumberInputView.kt"
    }

    private val numberOnClick= OnClickListener{
        numberEntered((it as Button).text.toString()[0])
    }

    private fun numberEntered(inputChar:Char){
        val currentText= binding.inputNumberTextView.text.toString()
        if(inputChar=='-'){
            val newText= if(currentText.contains("-")){ currentText.replace("-","")
            }else{ inputChar+currentText }
            binding.inputNumberTextView.text=newText
            return
        }
        if(inputChar=='.'){
            if(currentText.contains(".")){
                return
            }
        }
        if(inputChar.isDigit() || inputChar=='.') {
            if (currentText.filter { it.isDigit() }.length < maxDigits) {
                val newText = currentText + inputChar
                binding.inputNumberTextView.text = newText
            }
        }
    }

   var text:String
        get() {
            return binding.inputNumberTextView.text.toString()
        }
       set(value) {
           binding.inputNumberTextView.text = value
       }

    fun setMaxDigits(max:Int){
        maxDigits=max
    }

    fun onKeyPressed(keyCode:Int,keyEvent: KeyEvent){
        Log.d(TAG,"Keycode: $keyCode, unicode: ${keyEvent.unicodeChar}")
        if(keyEvent.action!=KeyEvent.ACTION_UP){
            if(keyCode==KEYCODE_DEL){
                deleteLast()
                return
            }
            numberEntered(keyEvent.unicodeChar.toChar())
        }
    }

    private fun deleteLast(){
        Log.d(TAG,"DeleteClicked")
        val currentText= binding.inputNumberTextView.text.toString()
        Log.d(TAG,"Deleting last from $currentText")
        binding.inputNumberTextView.text= currentText.dropLast(1)
    }
}