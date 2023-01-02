/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2012 Yuriy Kulikov yuriy.kulikov.87@gmail.com
 * Copyright (C) 2019 Yuriy Kulikov yuriy.kulikov.87@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.better.alarm.alert

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.better.alarm.CHANNEL_ID
import com.better.alarm.R
import com.better.alarm.background.Event
import com.better.alarm.configuration.Prefs
import com.better.alarm.configuration.Store
import com.better.alarm.interfaces.IAlarmsManager
import com.better.alarm.interfaces.Intents
import com.better.alarm.interfaces.PresentationToModelIntents
import com.better.alarm.notificationBuilder
import com.better.alarm.pendingIntentUpdateCurrentFlag
import com.better.alarm.presenter.AlarmDetailsFragment
import com.better.alarm.presenter.TransparentActivity
import com.better.alarm.util.formatToast
import com.better.alarm.util.subscribeForever
import java.text.SimpleDateFormat
import java.util.*

/**
 * Glue class: connects AlarmAlert IntentReceiver to AlarmAlert activity. Passes through Alarm ID.
 */
class BackgroundNotifications(
    private var mContext: Context,
    private val nm: NotificationManager,
    private val alarmsManager: IAlarmsManager,
    private val prefs: Prefs,
    private val store: Store
) {
  init {
    store.events.subscribeForever { event ->
      when (event) {
        is Event.AlarmEvent -> nm.cancel(event.id + SNOOZE_NOTIFICATION)
        is Event.PrealarmEvent -> nm.cancel(event.id + SNOOZE_NOTIFICATION)
        is Event.DismissEvent -> nm.cancel(event.id + SNOOZE_NOTIFICATION)
        is Event.CancelSnoozedEvent -> nm.cancel(event.id + SNOOZE_NOTIFICATION)
        is Event.SnoozedEvent -> onSnoozed(event.id, event.calendar)
        is Event.CancelPenaltyEvent -> nm.cancel(event.id + SNOOZE_NOTIFICATION)
        is Event.PenaltyEvent -> submitMessageMission()
        is Event.Autosilenced -> onSoundExpired(event.id)
        is Event.ShowSkip -> onShowSkip(event.id)
        is Event.HideSkip -> nm.cancel(SKIP_NOTIFICATION + event.id)
        is Event.DemuteEvent,
        is Event.MuteEvent,
        is Event.NullEvent -> Unit
      }
    }
  }

  private fun onSnoozed(id: Int, calendar: Calendar) {
    // When button Reschedule is clicked, the TransparentActivity with
    // TimePickerFragment to set new alarm time is launched
      val nowTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(System.currentTimeMillis()))
      val penaltyTime = SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(Calendar.getInstance().add(Calendar.))
      Log.d("지금시간", " $nowTime")
      Log.d("알람시간", " $penaltyTime")

    val pendingReschedule =
        Intent()
            .apply {
              setClass(mContext, TransparentActivity::class.java)
              putExtra(Intents.EXTRA_ID, id)
            }
            .let { PendingIntent.getActivity(mContext, id, it, pendingIntentUpdateCurrentFlag()) }

    val pendingDismiss =
        PresentationToModelIntents.createPendingIntent(
            mContext, PresentationToModelIntents.ACTION_REQUEST_DISMISS, id)

    val label = alarmsManager.getAlarm(id)?.labelOrDefault ?: ""

    val contentText: String =
        alarmsManager.getAlarm(id)?.let {
          mContext.getString(R.string.alarm_notify_snooze_text, calendar.formatTimeString())
        }
            ?: ""

    val status =
        mContext.notificationBuilder(CHANNEL_ID) {
          // Get the display time for the snooze and update the notification.
          setContentTitle(getString(R.string.alarm_notify_snooze_label, label))
          setContentText(contentText)
          setSmallIcon(R.drawable.stat_notify_alarm)
          setContentIntent(pendingDismiss)
          setOngoing(true)
          addAction(
              R.drawable.ic_action_reschedule_snooze,
              getString(R.string.alarm_alert_reschedule_text),
              pendingReschedule)
          addAction(
              R.drawable.ic_action_dismiss,
              getString(R.string.alarm_alert_dismiss_text),
              pendingDismiss)
          setDefaults(Notification.DEFAULT_LIGHTS)
        }

    // Send the notification using the alarm id to easily identify the
    // correct notification.
    nm.notify(id + SNOOZE_NOTIFICATION, status)
  }

  private fun getString(id: Int, vararg args: String) = mContext.getString(id, *args)
  private fun getString(id: Int) = mContext.getString(id)

  private fun Calendar.formatTimeString(): String {
    val format = if (prefs.is24HourFormat.blockingGet()) DM24 else DM12
    return DateFormat.format(format, this) as String
  }

  private fun onSoundExpired(id: Int) {
    // Update the notification to indicate that the alert has been
    // silenced.
    val alarm = alarmsManager.getAlarm(id)
    val label: String = alarm?.labelOrDefault ?: ""
    val autoSilenceMinutes = prefs.autoSilence.value
    val text = mContext.getString(R.string.alarm_alert_alert_silenced, autoSilenceMinutes)

    val notification =
        mContext.notificationBuilder(CHANNEL_ID) {
          setAutoCancel(true)
          setSmallIcon(R.drawable.stat_notify_alarm)
          setWhen(Calendar.getInstance().timeInMillis)
          setContentTitle(label)
          setContentText(text)
          setTicker(text)
        }

    nm.notify(ONLY_MANUAL_DISMISS_OFFSET + id, notification)
  }

  private fun onShowSkip(id: Int) {
    val pendingSkip =
        PresentationToModelIntents.createPendingIntent(
            mContext, PresentationToModelIntents.ACTION_REQUEST_SKIP, id)

    alarmsManager.getAlarm(id)?.run {
      val label: String = labelOrDefault

      val notification =
          mContext.notificationBuilder(CHANNEL_ID) {
            setAutoCancel(true)
            addAction(
                R.drawable.ic_action_dismiss,
                when {
                  data.daysOfWeek.isRepeatSet -> getString(R.string.skip)
                  else -> getString(R.string.disable_alarm)
                },
                pendingSkip)
            setSmallIcon(R.drawable.stat_notify_alarm)
            setWhen(Calendar.getInstance().timeInMillis)
            setContentTitle(label)
            setContentText(
                "${getString(R.string.notification_alarm_is_about_to_go_off)} ${data.nextTime.formatTimeString()}")
            setTicker(
                "${getString(R.string.notification_alarm_is_about_to_go_off)} ${data.nextTime.formatTimeString()}")
          }

      nm.notify(SKIP_NOTIFICATION + id, notification)
    }
  }

  // 미션 실행을 위한 함수
  private fun submitMessageMission() {
      val nowTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(getTime())
      val alarmTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(Calendar.getInstance().formatTimeString())

      Log.d("지금시간 vs 알람시간", " $nowTime, $alarmTime")
      // val telNumber = getPhoneNumber()
      val intentSent: Intent = Intent("SMS_SENT_ACTION")
      val intentDelivery: Intent = Intent("SMS_DELIVERED_ACTION")
      val sentIntent = PendingIntent.getBroadcast(mContext, 0, intentSent, 0)
      val deliveredIntent = PendingIntent.getBroadcast(mContext, 0, intentDelivery, 0)

      mContext?.registerReceiver(object: BroadcastReceiver() {
          override fun onReceive(context: Context?, intent: Intent?) {
              when (resultCode) {
                  AppCompatActivity.RESULT_OK -> {
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
          } }, IntentFilter("SMS_SENT_ACTION"))

      // SMS가 도착했을 때 실행
      mContext?.registerReceiver(object: BroadcastReceiver() {
          override fun onReceive(context: Context?, intent: Intent?) {
              when (resultCode) {
                  AppCompatActivity.RESULT_OK -> {
                      Toast.makeText(context, "SMS 도착 완료", Toast.LENGTH_SHORT)
                  }
                  AppCompatActivity.RESULT_CANCELED -> {
                      Toast.makeText(context, "SMS 도착 실패", Toast.LENGTH_SHORT)
                  }
              }
          }
      }, IntentFilter("SMS_DELIVERED_ACTION"))
      val SmsManager = SmsManager.getDefault()
      SmsManager.sendTextMessage(getPhoneNumber(), null, "당신 생각이 나서 연락했어요..", sentIntent, deliveredIntent)
  }

  fun getTime(): String {
      var now = System.currentTimeMillis()
      var date = Date(now)

      var dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
      var getTime = dateFormat.format(date)

      return getTime
  }

  @SuppressLint("Range")
  private fun getPhoneNumber(): String? {
        val names: MutableList<AlarmDetailsFragment.Contact> = arrayListOf()
        val cr = mContext.contentResolver
        val cur = cr?.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
            null, null, null)
        if (cur!!.count > 0) {
            while (cur.moveToNext()) {
                val id = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NAME_RAW_CONTACT_ID))
                val name = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val number = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                names.add(AlarmDetailsFragment.Contact(id, name, number))
            }
        }

        val range = (0..names.size-1)

        return names.get(range.random()).number
    }

  companion object {
    private const val DM12 = "E h:mm aa"
    private const val DM24 = "E kk:mm"
    private const val SNOOZE_NOTIFICATION = 1000
    private const val ONLY_MANUAL_DISMISS_OFFSET = 2000
    private const val SKIP_NOTIFICATION = 3000
  }
}
