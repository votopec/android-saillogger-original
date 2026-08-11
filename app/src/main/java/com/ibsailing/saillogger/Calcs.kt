package com.ibsailing.saillogger

import java.lang.Math.toRadians
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

fun Double.format(digits: Int) = "%.${digits}f".format(this)

fun Double.roundDecimals(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}

fun Float.roundDecimals(decimals: Int): Float {
    var multiplier = 1.0f
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}


/**
 * Gets boat color for boat number
 * @param boatNo boat number
 * @return RGB color of boat
 */
fun getBoatColor(boatNo: Int): Int {
    return when (boatNo % 20) {
        0 -> 0xFF808080u.toInt()//ContextCompat.getColor(context, R.color.LightgreyBoat)
        1 -> 0xFFFF2222u.toInt()//ContextCompat.getColor(context, R.color.RedBoat)
        2 -> 0xFF2222FFu.toInt()//ContextCompat.getColor(context, R.color.BlueBoat)
        3 -> 0xFF22AA22u.toInt()//ContextCompat.getColor(context, R.color.GreenBoat)
        4 -> 0xFFC0C000u.toInt()//ContextCompat.getColor(context, R.color.YellowBoat)
        5 -> 0xFF00A0A0u.toInt()//ContextCompat.getColor(context, R.color.CyanBoat)
        6 -> 0xFFDD00DDu.toInt()//ContextCompat.getColor(context, R.color.FuchsiaBoat)
        7 -> 0xFF6200EEu.toInt()//ContextCompat.getColor(context, R.color.PurpleBoat)
        8 -> 0xFFA00000u.toInt()//ContextCompat.getColor(context, R.color.MaroonBoat)
        9 -> 0xFF404040u.toInt()//ContextCompat.getColor(context, R.color.GrayBoat)
        10 -> 0xFF8B4513u.toInt()//ContextCompat.getColor(context, R.color.BrownBoat)
        11 -> 0xFFFFA500u.toInt()//ContextCompat.getColor(context, R.color.OrangeBoat)
        12 -> 0xFF808000u.toInt()//Olive
        13 -> 0xFF000080u.toInt()//Navy
        14 -> 0xFF008080u.toInt()//Teal
        15 -> 0xFFDAA520u.toInt()//GoldenRod
        16 -> 0xFF6A5ACDu.toInt()//SlateBlue
        17 -> 0xFFDC143Cu.toInt()//Crimson
        18 -> 0xFFFF69B4.toInt()//HotPink
        19 -> 0xFFFF4500.toInt()//Orange Red
        else -> 0
    }
}

fun getLineBiasDistance(portLoc: Loc, stbdLoc: Loc, windDir: Float): Float {
    val flatDistanceTo = portLoc.flatDistanceTo(stbdLoc)
    return (kotlin.math.sin(toRadians(getLineBiasAngle(portLoc, stbdLoc, windDir).toDouble())) * flatDistanceTo).toFloat()
}


fun getLineBiasAngle(portLoc: Loc, stbdLoc: Loc, windDir: Float): Float {
    return -(angleDifference(windDir, stbdLoc.flatBearingTo(portLoc).toFloat()) + 90)
}


/**
 * Calculates difference of two angles
 * @param theta1 first angle in degrees
 * @param theta2 seconf angle in degrees
 * @return angle difference. If theta1 is counter clockwise from theta2 returns negative number
 */
fun angleDifference(theta1: Float, theta2: Float): Float {
    var dif = fixAngle(theta2 - theta1)
    if (dif >= 180f) {
        dif -= 360f
    }
    return dif
}

/**
 * makes angle fit 0-360
 * @param angle angle to fix
 * @return angle between 0 and 360
 */
fun fixAngle(angle: Float): Float {
    var result = angle
    while (result >= 360) {
        result -= 360f
    }
    while (result < 0) {
        result += 360f
    }
    return result
}


/**
 * Returns string with start line data
 */
fun getStartLineString(eventList: ArrayList<LogEvent>, latitude: Double, longitude: Double,timeStamp:Long): String {

    val lastPinLat = eventList.findLast { it.eventType == EventType.PORT_START }?.latitude
    val lastPinLon = eventList.findLast { it.eventType == EventType.PORT_START }?.longitude

    val lastRcLat = eventList.findLast { it.eventType == EventType.STARBOARD_START }?.latitude
    val lastRcLon = eventList.findLast { it.eventType == EventType.STARBOARD_START }?.longitude

    val lastTime= eventList.findLast { it.eventType == EventType.START_TIME }?.startTimeStamp
    if (lastPinLat == null || lastPinLon == null || lastRcLat == null || lastRcLon == null) return ""

    val rcLoc = Loc(lastRcLat, lastRcLon)
    val pinLoc = Loc(lastPinLat, lastPinLon)
    val lineLength = rcLoc.flatDistanceTo(pinLoc)
    val lineBearing = rcLoc.flatBearingTo(pinLoc)
    val lengthString = "Line: ${lineLength.roundToInt()}m, bear:${lineBearing.roundToInt()}"
    val windDir = fixAngle(lineBearing.toFloat() + 90f)
    val windDirString = "For Wind:${windDir.roundToInt()}"
    var startBias=5
    val biasStringBuilder=StringBuilder()
    while(startBias<=20){
        biasStringBuilder.append("$startBias->${getLineBiasDistance(pinLoc, rcLoc, windDir + startBias.toFloat()).roundToInt()}m, ")
        startBias+=5
    }

    val biasString =biasStringBuilder.toString()
    val dtlString = "Distance To Line: ${Loc(latitude, longitude).distanceToLine(pinLoc, rcLoc).roundToInt()}m"
  val timeString=  if(lastTime!=null && timeStamp!=0L){"\nTime:${getCountDownTimeString(lastTime-timeStamp)}"}else{""}
    return "$lengthString. $windDirString\n$biasString\n$dtlString$timeString"
}

private fun getCountDownTimeString(timeStamp: Long): String {
    val timeString: String
    val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeStamp), ZoneId.of("UTC"))
    timeString = date.format(DateTimeFormatter.ofPattern("mm:ss"))
    return timeString
}

