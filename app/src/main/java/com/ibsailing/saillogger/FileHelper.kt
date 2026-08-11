package com.ibsailing.saillogger

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


fun saveEvents(logEventList:ArrayList<LogEvent>, context:Context,clearEvents:Boolean=false){
    CoroutineScope(Dispatchers.IO).launch {

        logEventList.forEach { it.latitude=it.latitude.roundDecimals(7)
        it.longitude=it.longitude.roundDecimals(7)}
    if(logEventList.isNotEmpty()){
            val gson = Gson()
            val stringToWrite = gson.toJson(logEventList)
            //  val fileTimeStamp = logPointList.first().timeStamp
            val fileName = getFileNameEvents(logEventList.first().startTimeStamp)
            val saveFile = File(context.getExternalFilesDir(null), fileName)
            try {
                withContext(Dispatchers.IO) {
                    val fileWriter = FileWriter(saveFile, false)
                    fileWriter.write(stringToWrite)
                    fileWriter.close()
                }
                if(clearEvents){
                    logEventList.clear()
                }
            } catch (e: Exception) {
                Log.e(TAG, "File Write Exception")
            }
        }
    }
}

private fun getFileNameEvents(timeStamp: Long): String {
    var fileName: String
    val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeStamp), ZoneId.systemDefault())
    fileName = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))
    fileName += "_Events.txt"
    return fileName
}




fun Context.getBoatDrawable(drawableID:Int,boatNumber:Int): Drawable {
    val drawable= ContextCompat.getDrawable(this, drawableID)
    drawable?.setTint(getBoatColor(boatNumber))
    return drawable!!
}

/**
 * Returns string of yyyy-MM-dd HH:mm:ss
 */
fun getDateTimeString(timeStamp:Long, zoneId: ZoneId):String{
    val date = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(timeStamp),
        zoneId
    )
    return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}

private const val TAG="FileHelper.kt"