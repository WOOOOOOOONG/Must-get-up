package lilcode.aop.p3.c03.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import java.util.*

class MainActivity : AppCompatActivity() {
    val MY_PERMISSION_ACCESS_ALL = 100
    val mContext = this

    override fun onCreate(savedInstanceState: Bundle?) {
        // 권한 설정
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            val permissions = arrayOf(
                android.Manifest.permission.CALL_PHONE,
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.READ_PHONE_NUMBERS,
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.WRITE_CONTACTS,
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.INTERNET,
            )
            ActivityCompat.requestPermissions(this, permissions, MY_PERMISSION_ACCESS_ALL)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 뷰를 초기화 해주기
        initOnOffButton()

        // 저장된 데이터 가져오기
        val model = fetchDataFromSharedPreferences()
        val model2 = fetchDataFromSharedPreferences2()

        // 뷰에 데이터를 그려주기
        renderView(model)
        renderView2(model2)
    }

    /******************************************************************
     * ************************데이터 저장*******************************
     * ****************************************************************/

    // 이전 데이터 저장1(알람 on/off)
    private fun fetchDataFromSharedPreferences(): AlarmDisplayModel {
        val sharedPreferences = getSharedPreferences(M_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)

        // DB 에서 데이터 가져오기
        val timeDBValue = sharedPreferences.getString(M_ALARM_KEY, "09:30") ?: "09:30"
        val onOffDBValue = sharedPreferences.getBoolean(M_ONOFF_KEY, false)

        // 시:분 형식으로 가져온 데이터 스플릿
        val alarmData = timeDBValue.split(":")

        val alarmModel = AlarmDisplayModel(alarmData[0].toInt(), alarmData[1].toInt(), onOffDBValue)

        // 보정 조정 예외처 (브로드 캐스트 가져오기)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            M_ALARM_REQUEST_CODE,
            Intent(this, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE
        ) // 있으면 가져오고 없으면 안만든다. (null)

        if ((pendingIntent == null) and alarmModel.onOff) {
            //알람은 꺼져있는데, 데이터는 켜져있는 경우
            alarmModel.onOff = false

        } else if ((pendingIntent != null) and alarmModel.onOff.not()) {
            // 알람은 켜져있는데 데이터는 꺼져있는 경우.
            // 알람을 취소함
            pendingIntent.cancel()
        }
        return alarmModel
    }

    // 이전 데이터 저장2(미션 설정)
    private fun fetchDataFromSharedPreferences2(): String {
        val sharedPreferences = getSharedPreferences(M_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)

        // DB 에서 데이터 가져오기
        val missionDBValue = sharedPreferences.getString(M_MISSION, "현재 미션 : 랜덤 문자")

        // 보정 조정 예외처 (브로드 캐스트 가져오기)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            M_ALARM_REQUEST_CODE,
            Intent(this, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE
        ) // 있으면 가져오고 없으면 안만든다. (null)

        return missionDBValue.toString()
    }

    // 알람 시간 DB에 저장
    private fun saveAlarmModel(hour: Int, minute: Int, onOff: Boolean): AlarmDisplayModel {
        val model = AlarmDisplayModel(
            hour = hour,
            minute = minute,
            onOff = onOff
        )

        // time 에 대한 db 파일 생성
        val sharedPreferences = getSharedPreferences(M_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)

        // edit 모드로 열어서 작업 (값 저장)
        with(sharedPreferences.edit()) {
            putString(M_ALARM_KEY, model.makeDataForDB())
            putBoolean(M_ONOFF_KEY, model.onOff)
            commit()
        }

        return model
    }

    /******************************************************************
     * ************************onClick*********************************
     * ****************************************************************/

    // onclick : 시간 변경
    @Override
    public fun onClick1(v: View) {
        v.setOnClickListener {
            // 현재 시간을 가져오기 위해 캘린더 인스터늣 사
            val calendar = Calendar.getInstance()
            // TimePickDialog 띄워줘서 시간을 설정을 하게끔 하고, 그 시간을 가져와서
            TimePickerDialog(this, { picker, hour, minute ->


                // 데이터를 저장
                val model = saveAlarmModel(hour, minute, false)
                // 뷰를 업데이트
                renderView(model)

                // 기존에 있던 알람을 삭제한다.
                cancelAlarm()

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
                .show()

        }
    }

    // onclick : 미션 선택 후 저장 및 토스트
    public fun onClick2(b: View) {
        b.setOnClickListener {
            var popupMenu = PopupMenu(applicationContext, it)
            menuInflater?.inflate(R.menu.alarm_menu, popupMenu.menu)
            popupMenu.show()
            popupMenu.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.misson1 -> {
                        Toast.makeText(applicationContext, "'랜덤 전화'를 선택하셨습니다", Toast.LENGTH_SHORT)
                            .show()
                        b.findViewById<Button>(R.id.mission).setText("현재 미션 : 랜덤 전화")

                        val sharedPreferences = getSharedPreferences(M_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)

                        // edit 모드로 열어서 작업 (값 저장)
                        with(sharedPreferences.edit()) {
                            putString(M_MISSION, b.findViewById<Button>(R.id.mission).text.toString())
                            commit()
                        }

                        return@setOnMenuItemClickListener true
                    }
                    R.id.misson2 -> {
                        Toast.makeText(applicationContext, "'랜덤 문자'를 선택하셨습니다", Toast.LENGTH_SHORT)
                            .show()
                        b.findViewById<Button>(R.id.mission).setText("현재 미션 : 랜덤 문자")

                        val sharedPreferences = getSharedPreferences(M_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)

                        // edit 모드로 열어서 작업 (값 저장)
                        with(sharedPreferences.edit()) {
                            putString(M_MISSION, b.findViewById<Button>(R.id.mission).text.toString())
                            commit()
                        }

                        return@setOnMenuItemClickListener true
                    }
                    R.id.misson3 -> {
                        Toast.makeText(
                            applicationContext,
                            "'효자/효녀는 웁니다'를 선택하셨습니다",
                            Toast.LENGTH_SHORT
                        ).show()
                        b.findViewById<Button>(R.id.mission).setText("현재 미션 : 효자/효녀는 웁니다")

                        val sharedPreferences = getSharedPreferences(M_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)

                        // edit 모드로 열어서 작업 (값 저장)
                        with(sharedPreferences.edit()) {
                            putString(M_MISSION, b.findViewById<Button>(R.id.mission).text.toString())
                            commit()
                        }

                        return@setOnMenuItemClickListener true
                    }
                    else -> {
                        return@setOnMenuItemClickListener false
                    }
                }
            }
        }
    }

    // onclick : 설명 창
    public fun onClickIntro(b: View) {
        b.setOnClickListener {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            val text: String = """
                -이 앱은 기본적으로 1개의 알람을 지정할 수 있으며, 매일 반복될 것입니다.
                
                -개발자는 절대로 사용자의 기기에 임의로 접근하여 탈취하지 않습니다.
                
                -미션에 따라, 최초 알람으로부터 10분 이내에 일어나지 않을 시 벌칙 내용입니다.
                ■랜덤 전화 : 당신의 전화번호부에 있는 사람에게 랜덤으로 전화가 갑니다. 선생님, 교수님 또는 직장 상사에게 전화가 간다면 재밌겠죠?
                ■랜덤 문자 : 당신의 전화번호부에 있는 사람에게 랜덤으로 "당신 생각이 나서 연락했어요.." 라는 내용의 문자 메시지가 전송됩니다.
                ■내가 설정한 노래가 아니야 : j-pop이 굉장히 크게 틉니다. 혹시 옆 방에 계신 분도 들릴지도? 
            """.trimIndent()
            builder.setTitle("앱 설명").setMessage(text)

            val alertDialog: AlertDialog = builder.create()

            alertDialog.show()
        }
    }

    // onclick : 요일 반복 시 토스트창
    public fun onClickWeek(v: View) {
        v.setOnClickListener {
            when {
                v?.id == R.id.mon && findViewById<ToggleButton>(R.id.mon).isChecked == true
                -> Toast.makeText(applicationContext, "월요일에 알람을 설정했습니다.", Toast.LENGTH_SHORT).show()
                v?.id == R.id.mon && findViewById<ToggleButton>(R.id.mon).isChecked == false
                -> Toast.makeText(applicationContext, "월요일에 알람을 해제했습니다.", Toast.LENGTH_SHORT).show()

                v?.id == R.id.tue && findViewById<ToggleButton>(R.id.tue).isChecked == true
                -> Toast.makeText(applicationContext, "화요일에 알람을 설정했습니다.", Toast.LENGTH_SHORT).show()
                v?.id == R.id.tue && findViewById<ToggleButton>(R.id.tue).isChecked == false
                -> Toast.makeText(applicationContext, "화요일에 알람을 해제했습니다.", Toast.LENGTH_SHORT).show()

                v?.id == R.id.wen && findViewById<ToggleButton>(R.id.wen).isChecked == true
                -> Toast.makeText(applicationContext, "수요일에 알람을 설정했습니다.", Toast.LENGTH_SHORT).show()
                v?.id == R.id.wen && findViewById<ToggleButton>(R.id.wen).isChecked == false
                -> Toast.makeText(applicationContext, "수요일에 알람을 해제했습니다.", Toast.LENGTH_SHORT).show()

                v?.id == R.id.thu && findViewById<ToggleButton>(R.id.thu).isChecked == true
                -> Toast.makeText(applicationContext, "목요일에 알람을 설정했습니다.", Toast.LENGTH_SHORT).show()
                v?.id == R.id.thu && findViewById<ToggleButton>(R.id.thu).isChecked == false
                -> Toast.makeText(applicationContext, "목요일에 알람을 해제했습니다.", Toast.LENGTH_SHORT).show()

                v?.id == R.id.fri && findViewById<ToggleButton>(R.id.fri).isChecked == true
                -> Toast.makeText(applicationContext, "금요일에 알람을 설정했습니다.", Toast.LENGTH_SHORT).show()
                v?.id == R.id.fri && findViewById<ToggleButton>(R.id.fri).isChecked == false
                -> Toast.makeText(applicationContext, "금요일에 알람을 해제했습니다.", Toast.LENGTH_SHORT).show()

                v?.id == R.id.sat && findViewById<ToggleButton>(R.id.sat).isChecked == true
                -> Toast.makeText(applicationContext, "토요일에 알람을 설정했습니다.", Toast.LENGTH_SHORT).show()
                v?.id == R.id.sat && findViewById<ToggleButton>(R.id.sat).isChecked == false
                -> Toast.makeText(applicationContext, "토요일에 알람을 해제했습니다.", Toast.LENGTH_SHORT).show()

                v?.id == R.id.sun && findViewById<ToggleButton>(R.id.sun).isChecked == true
                -> Toast.makeText(applicationContext, "일요일에 알람을 설정했습니다.", Toast.LENGTH_SHORT).show()
                v?.id == R.id.sun && findViewById<ToggleButton>(R.id.sun).isChecked == false
                -> Toast.makeText(applicationContext, "일요일에 알람을 해제했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /******************************************************************
     * *************************미션 함수*******************************
     * ****************************************************************/

    // 미션1: 랜덤으로 전화 걸기
    @Override
    public fun callMission() {
        val telNumber = getPhoneNumber()
        val permissionListener = object : PermissionListener {
            override fun onPermissionGranted() {

                val myUri = Uri.parse("tel:${telNumber}")
                val myIntent = Intent(Intent.ACTION_CALL, myUri)
                startActivity(myIntent)

            }

            override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                Toast.makeText(mContext,"전화 연결 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()

            }

        }
        TedPermission.with(mContext)
            .setPermissionListener(permissionListener)
            .setDeniedMessage("[설정] 에서 권한을 열어줘야 전화 연결이 가능합니다.")
            .setPermissions(Manifest.permission.CALL_PHONE)
            .check()
    }

    // 미션을 위한 전화번호 얻기 함수
    @SuppressLint("Range")
    private fun getPhoneNumber(): String? {
        val names: MutableList<Contact> = arrayListOf()
        val cr = contentResolver
        val cur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
            null, null, null)
        if (cur!!.count > 0) {
            while (cur.moveToNext()) {
                val id = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NAME_RAW_CONTACT_ID))
                val name = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val number = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                names.add(Contact(id , name , number))
            }
        }

        val range = (0..names.size-1)

        return names.get(range.random()).number
    }

    // 미션2: 메시지 보내기
    public fun submitMessageMission() {
        val telNumber = getPhoneNumber()
        val intentSent: Intent = Intent("SMS_SENT_ACTION")
        val intentDelivery: Intent = Intent("SMS_DELIVERED_ACTION")
        val sentIntent = PendingIntent.getBroadcast(this, 0, intentSent, 0)
        val deliveredIntent = PendingIntent.getBroadcast(this, 0, intentDelivery, 0)

        registerReceiver(object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Toast.makeText(context, "전송 완료", Toast.LENGTH_SHORT)
                    }
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        Toast.makeText(context, "전송 실패", Toast.LENGTH_SHORT)
                    }
                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        Toast.makeText(context, "서비스 지역이 아닙니다", Toast.LENGTH_SHORT)
                    }
                    SmsManager.RESULT_ERROR_RADIO_OFF -> {
                        Toast.makeText(context, "휴대폰이 꺼져있습니다", Toast.LENGTH_SHORT)
                    }
                    SmsManager.RESULT_ERROR_NULL_PDU -> {
                        Toast.makeText(context, "PDU Null", Toast.LENGTH_SHORT)
                    }
                }
            }
        }, IntentFilter("SMS_SENT_ACTION"))
        // SMS가 도착했을 때 실행
        registerReceiver(object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Toast.makeText(context, "SMS 도착 완료", Toast.LENGTH_SHORT)
                    }
                    Activity.RESULT_CANCELED -> {
                        Toast.makeText(context, "SMS 도착 실패", Toast.LENGTH_SHORT)
                    }
                }
            }
        }, IntentFilter("SMS_DELIVERED_ACTION"))
        val SmsManager = SmsManager.getDefault()
        SmsManager.sendTextMessage(getPhoneNumber(), null, "당신 생각이 나서 연락했어요..", sentIntent, deliveredIntent)
    }

    /******************************************************************
     * ******************************알람*******************************
     * ****************************************************************/

    // 알람 켜기 끄기 버튼.
    private fun initOnOffButton() {
        val onOffButton = findViewById<Button>(R.id.onOffButton)
        onOffButton.setOnClickListener {
            // 저장한 데이터를 확인한다
            val model =
                it.tag as? AlarmDisplayModel ?: return@setOnClickListener// 형변환 실패하는 경우에는 null
            val newModel = saveAlarmModel(model.hour, model.minute, model.onOff.not()) // on off 스위칭
            renderView(newModel)

            // 온/오프 에 따라 작업을 처리한다
            if (newModel.onOff) {
                // 요일별 알람 등록
                val week = booleanArrayOf(
                    false,
                    findViewById<ToggleButton>(R.id.sun).isChecked,
                    findViewById<ToggleButton>(R.id.mon).isChecked,
                    findViewById<ToggleButton>(R.id.tue).isChecked,
                    findViewById<ToggleButton>(R.id.wen).isChecked,
                    findViewById<ToggleButton>(R.id.thu).isChecked,
                    findViewById<ToggleButton>(R.id.fri).isChecked,
                    findViewById<ToggleButton>(R.id.sat).isChecked,
                )

                // 온 -> 알람을 등록
                val calender = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, newModel.hour)
                    set(Calendar.MINUTE, newModel.minute)
                    // 지나간 시간의 경우 다음날 알람으로 울리도록
                    if (before(Calendar.getInstance())) {
                        add(Calendar.DATE, 1) // 하루 더하기
                    }
                }

                //알람 매니저 가져오기.
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

                val intent = Intent(this, AlarmReceiver::class.java)
                intent.putExtra("day_of_week", week)
                val pendingIntent = PendingIntent.getBroadcast(
                    this,
                    M_ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                ) // 있으면 새로 만든거로 업데이트

                // 잠자기 모드에서도 허용 하는 방법
//                alarmManager.setAndAllowWhileIdle()
//                alarmManager.setExactAndAllowWhileIdle()

                alarmManager.setInexactRepeating( // 정시에 반복
                    AlarmManager.RTC_WAKEUP, // RTC_WAKEUP : 실제 시간 기준으로 wakeup , ELAPSED_REALTIME_WAKEUP : 부팅 시간 기준으로 wakeup
                    calender.timeInMillis, // 언제 알람이 발동할지.
                    AlarmManager.INTERVAL_DAY, // 하루에 한번씩
                    pendingIntent
                )
            } else {
                // 오프 -> 알람을 제거
                cancelAlarm()
            }


        }
    }

    // 저장된 시간을 화면에 표시
    private fun renderView(model: AlarmDisplayModel) {
        // 최초 실행 또는 시간 재설정 시 들어옴

        findViewById<TextView>(R.id.ampmTextView).apply {
            text = model.ampmText
        }
        findViewById<TextView>(R.id.timeTextView).apply {
            text = model.timeText
        }
        findViewById<Button>(R.id.onOffButton).apply {
            text = model.onOffText
            tag = model
        }
    }

    // 저장된 미션을 화면에 표시
    private fun renderView2(mission: String) {
        // 최초 실행 또는 시간 재설정 시 들어옴

        findViewById<Button>(R.id.mission).apply {
            text = mission
        }
    }

    // 알람 off
    private fun cancelAlarm() {
        // 기존에 있던 알람을 삭제한다.
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            M_ALARM_REQUEST_CODE,
            Intent(this, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE
        ) // 있으면 가져오고 없으면 안만든다. (null)

        pendingIntent?.cancel() // 기존 알람 삭제
    }

    // 알람 설정하기 위한 화면 표시
    private fun showPopup(v: View) {
        val popup = PopupMenu(this, v)
        popup.menuInflater.inflate(R.menu.alarm_menu, popup.menu)
        popup.show()
    }

    /******************************************************************
     * ******************************객체*******************************
     * ****************************************************************/

    // 권한 객체
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode === MY_PERMISSION_ACCESS_ALL) {
            if (grantResults.size > 0) {
                for (grant in grantResults) {
                    if (grant != PackageManager.PERMISSION_GRANTED) System.exit(0)
                }
            }
        }
    }

    // 미션을 위한 전화번호 클래스
    data class Contact(
        val id : String ,
        val name : String,
        val number : String)

    // 기타 필요한 변수들
    companion object {
        // static 영역 (상수 지정)
        private const val M_SHARED_PREFERENCE_NAME = "time"
        private const val M_ALARM_KEY = "alarm"
        private const val M_ONOFF_KEY = "onOff"
        private const val M_MISSION = "mission"
        private const val M_ALARM_REQUEST_CODE = 1000
    }
}