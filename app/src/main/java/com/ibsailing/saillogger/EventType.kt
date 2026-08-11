package com.ibsailing.saillogger


//DONT CHANGE THE ORDER OF THESE
enum class EventType(val typeString:String) {

    STARBOARD_START("Starboard End"),
    PORT_START("Port End"),
    START_TIME("Start Time"),
    CUSTOM_TEXT("Text Event"),
    WINDWARDMARK("WW Mark"),
    LEEWARDMARK("LW Mark"),
    OFFSETMARK("Offset Mark"),
    LEEWARDGATEPORT("Port Gate"),
    LEEWARDGATESTBD("Stbd Gate"),
    VIDEO("Video"),
    PHOTO("Photo");

    companion object {
        private val map = EventType.values().associateBy(EventType::typeString)
        fun fromString(type: String) = map[type]
    }
}