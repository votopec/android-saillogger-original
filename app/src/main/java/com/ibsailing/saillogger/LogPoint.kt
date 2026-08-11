package com.ibsailing.saillogger

import android.location.Location
import java.lang.StringBuilder
import kotlin.math.roundToInt


class LogPoint {

    var timeStamp=0L
                var heading:Double?=null
                var heel:Double?=null
                var pitch:Double? = null

    var magnetX:Float?=null
    var magnetY:Float?=null
    var magnetZ:Float?=null

    //

    var location:Location?=null



fun getCsvString(isLoggingHeel:Boolean,isLoggingPitch:Boolean,isLoggingHeading:Boolean,isLoggingAltitude:Boolean,isLoggingMagnet:Boolean):String{
            val sb = StringBuilder()
          sb.append(timeStamp)
            sb.append(",")
            location?.let { sb.append(it.longitude.roundDecimals(7))}
            sb.append(",")
            location?.let {sb.append(it.latitude.roundDecimals(7))}
            sb.append(",")
            location?.let {sb.append((it.speed*1.94384).roundDecimals(2))}
            sb.append(",")
            location?.let {sb.append(it.bearing.roundDecimals(2))}

    if(isLoggingHeel){
        sb.append(",")
        sb.append(heel?.roundDecimals(2)?:"")
    }
    if(isLoggingPitch){
        sb.append(",")
        sb.append(pitch?.roundDecimals(2)?:"")
    }
         if(isLoggingHeading) {
             sb.append(",")
             sb.append(heading?.roundDecimals(1) ?: "")
         }

           if(isLoggingAltitude) {
               sb.append(",")
               location?.let { sb.append(it.altitude.roundToInt()) }
           }

    if(isLoggingMagnet) {
        sb.append(",")
        sb.append(magnetX?.roundDecimals(1) ?: "")
        sb.append(",")
        sb.append(magnetY?.roundDecimals(1) ?: "")
        sb.append(",")
        sb.append(magnetZ?.roundDecimals(1) ?: "")
    }
            return sb.toString()
        }

    companion object{
        const val TAG="LogPoint"
    }




}