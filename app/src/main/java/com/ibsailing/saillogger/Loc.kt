package com.ibsailing.saillogger


import android.util.Log
import java.lang.Math.toDegrees
import java.lang.Math.toRadians
import kotlin.math.*

data class Loc(var lat: Double = 1000.0, var lon: Double = 1000.0) {


    override fun equals(other: Any?): Boolean {
        val otherLoc = (other as Loc)
        return lat == otherLoc.lat && lon == otherLoc.lon
    }

    private var distPerLat = 0.0
    private var distPerLon = 0.0
    var longitudeRatio = 0.0

    init {
        //TODO use this check in the future
        distPerLon = (0.0003121092 * lat.pow(4.0) + 0.0101182384 * lat.pow(3.0) - 17.2385140059 * lat * lat) + 5.5485277537 * lat + 111301.967182595
        distPerLat = ((-0.000000487305676 * lat.pow(4.0)) - (0.0033668574 * lat.pow(3.0)) + (0.4601181791 * lat * lat) - (1.4558127346 * lat) + 110579.25662316)
        longitudeRatio = distPerLon / distPerLat
    }

    companion object {
        private const val TAG = "Loc.kt"
    }


    /**
     * Returns flat distance to location
     * @param location location to which distance is calculated
     * @return distance in meters
     */
    fun flatDistanceTo(location: Loc): Double {
        val a = (lat - location.lat) * distPerLat
        val b = (lon - location.lon) * distPerLon
        return sqrt(a * a + b * b)
    }


    /**
     * Returns flat bearing to location
     * @param location location to which bearing is calculated
     * @return bearing in degrees
     */
    fun flatBearingTo(location: Loc): Double {
        val a = (lat - location.lat) * distPerLat
        val b = (lon - location.lon) * distPerLon
        // return atan2(b, a) * 180
        return toDegrees(atan2(b, a)) + 180
    }

    /**
     *  Upwind distance to location
     *  @param location location to get upwind distance to
     *  @param windDir wind direction
     *  @return upwind distance in meters, negative if location is downwind
     */
//    fun upwindDistanceTo(location: Loc, windDir: Float): Float {
//        val bearingToOther = flatBearingTo(location).toFloat()
//        val angleDiff = angleDifference(bearingToOther, windDir)
//
//        val cos = getcos(toRadians(angleDiff)
//        val flatDistanceTo = flatDistanceTo(location).toFloat()
//
//        return cos * flatDistanceTo
//    }


    /**
     *     True if location is valid
     */
    val isValid: Boolean
        get() = (lat > -90.0 && lat < 90 && lon > -180 && lon < 180 && !(lat == 0.0 && lon == 0.0))


    /**
     * returns distance to line between lineLocation1 and lineLocation2 and beyond
     * @param firstLocation 1st point of line
     * @param secondLocation 2nd point of line
     * @return distance to line in m
     */
    fun distanceToLine(
        firstLocation: Loc,
        secondLocation: Loc
    ): Double {
        val distance: Double

        if (firstLocation.lon == secondLocation.lon && firstLocation.lat == secondLocation.lat) {
            return flatDistanceTo(firstLocation)
        }

        //Set the three points as coordinates with actual distances ( x1,y1=0,0 )
        val x1 = 0.0
        val y1 = 0.0

        val firstToSecondBearing = firstLocation.flatBearingTo(secondLocation)
        val firstToSecondDistance = firstLocation.flatDistanceTo(secondLocation)

        val firstToThisBearing = firstLocation.flatBearingTo(this)
        val firstToThisDistance = firstLocation.flatDistanceTo(this)

        if (firstToSecondBearing < 0) Log.w(TAG, "firstToSecondBearing angle<0")

        if (firstToThisBearing < 0) Log.w(TAG, "firstToThisBearing angle<0")

        val x2: Double = (cos(toRadians(firstToSecondBearing)) * firstToSecondDistance)
        val y2: Double = (sin(toRadians(firstToSecondBearing)) * firstToSecondDistance)

        val x: Double = (cos(toRadians(firstToThisBearing)) * firstToThisDistance)
        val y: Double = (sin(toRadians(firstToThisBearing)) * firstToThisDistance)
        val a = x - x1
        val b = y - y1
        val c = x2 - x1
        val d = y2 - y1
        //distance equals 2 x area of triangle of all 3 points / distance between line points
        distance = if (x1 == x2 && y1 == y2) {
            firstToThisDistance
        } else {
            (a * d - c * b) / sqrt(c * c + d * d)
        }
        return distance
    }
}