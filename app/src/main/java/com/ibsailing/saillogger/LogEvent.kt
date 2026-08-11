package com.ibsailing.saillogger

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.time.ZoneId
import java.util.*

@Keep
data class LogEvent(
    var latitude: Double =0.0,
    var longitude: Double=0.0,
    var startTimeStamp:Long=0L,
    var endTimeStamp:Long=0L,
    var boat:Int=0,
    var eventText:String="",
    var valueName:String="",
    var value:Float=0f,
    var eventType:EventType=EventType.CUSTOM_TEXT,
    var uuid:String= UUID.randomUUID().toString()
) {


    fun toCSV():String{
        return("${eventType.ordinal},${startTimeStamp},${latitude.roundDecimals(6)},${longitude.roundDecimals(6)},$eventText")
    }

    fun toShortString():String{
        return("${getDateTimeString(startTimeStamp, ZoneId.of("UTC"))}Z: ${eventType.typeString}, ${eventText}")
    }

}




