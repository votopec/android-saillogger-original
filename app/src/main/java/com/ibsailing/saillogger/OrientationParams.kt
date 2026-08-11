package com.ibsailing.saillogger

class OrientationParams(var pitch:Double,var heel:Double,var heading:Double) {

    fun rotate(rotationMode:Int):OrientationParams{
        val temp: Double
        //      adjust azimuth, based on rotation
        when (rotationMode) {
            MainActivity.BOWTOP -> {
                pitch = -pitch
                heading += 180f
                heading %= 360f
            }
            MainActivity.BOWBOTTOM -> {
                heel = -heel
                heading += 0f
                heading %= 360f
            }
            MainActivity.BOWLEFT -> {
                temp = pitch
                pitch = heel
                heel = temp
                heading += 90f
                heading %= 360f
            }
            MainActivity.BOWRIGHT -> {
                temp = pitch
                pitch = -heel
                heel = -temp
                heading += 270f
                heading %= 360f
            }
            MainActivity.UPRIGHT -> {
                temp = heel
                heel = -pitch
                pitch = -(temp+90)
                heading -= 90f
                heading+=360f
                heading %= 360f
            }
            MainActivity.UPLEFT -> {
                temp = heel
                heel = pitch
                pitch = temp-90
                heading += 90f
                heading %= 360f
            }
            MainActivity.UPRIGHTBACK -> {
                temp = heel
                heel = pitch
                pitch = (temp+90)
                heading += 90f
                heading %= 360f
            }
            MainActivity.UPLEFTBACK -> {
                temp = heel
                heel = -pitch
                pitch = -(temp-90)
                heading -= 90f
                heading+=360f
                heading %= 360f
            }


        }
        
        return OrientationParams(pitch,heel,heading)
    }

}