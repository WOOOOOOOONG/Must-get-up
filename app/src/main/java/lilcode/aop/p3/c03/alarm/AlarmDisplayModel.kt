package lilcode.aop.p3.c03.alarm

data class AlarmDisplayModel(
    val day: Int,
    val hour: Int, // 0~23
    val minute: Int,
    var onOff: Boolean
) {

    fun makeDataForDB(): String {
        return "$day:$hour:$minute"
    }

    // 형식에 맞게 시:분 가져오기.
    val timeText: String
        get() {
            val d = "%02d".format(day)
            val h = "%02d".format(if (hour < 12) hour else hour - 12)
            val m = "%02d".format(minute)

            return "$d:$h:$m"
        }

    // am pm 가져오기.
    val ampmText: String
        get() {
            return if (hour < 12) "AM" else "PM"
        }

    val onOffText: String
    get(){
        return if(onOff) "현재 : ON" else "현재 : OFF"
    }
}