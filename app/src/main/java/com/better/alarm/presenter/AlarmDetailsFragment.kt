/*
 * Copyright (C) 2017 Yuriy Kulikov yuriy.kulikov.87@gmail.com
 * Copyright (C) 2012 Yuriy Kulikov yuriy.kulikov.87@gmail.com
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

package com.better.alarm.presenter

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.better.alarm.R
import com.better.alarm.checkPermissions
import com.better.alarm.configuration.Layout
import com.better.alarm.configuration.Prefs
import com.better.alarm.configuration.globalInject
import com.better.alarm.configuration.globalLogger
import com.better.alarm.interfaces.IAlarmsManager
import com.better.alarm.logger.Logger
import com.better.alarm.lollipop
import com.better.alarm.model.AlarmValue
import com.better.alarm.model.Alarmtone
import com.better.alarm.model.ringtoneManagerString
import com.better.alarm.util.Optional
import com.better.alarm.util.modify
import com.better.alarm.view.showDialog
import com.better.alarm.view.summary
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.schedulers.Schedulers
import org.koin.dsl.koinApplication
import java.util.Calendar

/** Details activity allowing for fine-grained alarm modification */
class AlarmDetailsFragment : Fragment() {
  private val alarms: IAlarmsManager by globalInject()
  private val logger: Logger by globalLogger("AlarmDetailsFragment")
  private val prefs: Prefs by globalInject()
  private var disposables = CompositeDisposable()

  private var backButtonSub: Disposable = Disposables.disposed()
  private var disposableDialog = Disposables.disposed()

  private val alarmsListActivity by lazy { activity as AlarmsListActivity }
  private val store: UiStore by globalInject()

  private val rowHolder: RowHolder by lazy {
    RowHolder(fragmentView.findViewById(R.id.details_list_row_container), alarmId, prefs.layout())
  }

  private val editor: Observable<AlarmValue> by lazy {
    store.editing().filter { it.value.isPresent() }.map { it.value.get() }
  }

  private val alarmId: Int by lazy { store.editing().value!!.id }

  private val highlighter: ListRowHighlighter? by lazy {
    ListRowHighlighter.createFor(requireActivity().theme)
  }

  private lateinit var fragmentView: View

  private val ringtonePickerRequestCode = 42

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    logger.trace { "Showing details of ${store.editing().value}" }

    val view =
        inflater.inflate(
            when (prefs.layout()) {
              Layout.CLASSIC -> R.layout.details_fragment_classic
              Layout.COMPACT -> R.layout.details_fragment_compact
              else -> R.layout.details_fragment_bold
            },
            container,
            false)
    this.fragmentView = view

    disposables = CompositeDisposable()

    onCreateTopRowView()
    onCreateLabelView()
    onCreateRepeatView()
    onCreateRingtoneView()
    onCreatePenaltyView()
    onCreateDeleteOnDismissView()
    onCreatePrealarmView()
    onCreateBottomView()

    store.transitioningToNewAlarmDetails().takeFirst { isNewAlarm ->
      if (isNewAlarm) {
        showTimePicker()
      }
      store.transitioningToNewAlarmDetails().onNext(false)
    }

    return view
  }

  private fun onCreateBottomView() {
    fragmentView.findViewById<View>(R.id.details_activity_button_save).setOnClickListener {
      saveAlarm()
    }
    fragmentView.findViewById<View>(R.id.details_activity_button_revert).setOnClickListener {
      revert()
    }
  }

  private fun onCreateLabelView() {
    val label: EditText = fragmentView.findViewById<EditText>(R.id.details_label)

    observeEditor { value ->
      if (value.label != label.text.toString()) {
        label.setText(value.label)
      }
    }

    label.addTextChangedListener(
        object : TextWatcher {
          override fun afterTextChanged(s: Editable?) {}

          override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

          override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            editor.takeFirst {
              if (it.label != s.toString()) {
                modify("Label") { prev -> prev.copy(label = s.toString(), isEnabled = true) }
              }
            }
          }
        })
  }

  private fun onCreateRepeatView() {

    fragmentView.findViewById<LinearLayout>(R.id.details_repeat_row).setOnClickListener {
      editor
          .firstOrError()
          .flatMap { value -> value.daysOfWeek.showDialog(requireContext()) }
          .subscribe { daysOfWeek ->
            modify("Repeat dialog") { prev -> prev.copy(daysOfWeek = daysOfWeek, isEnabled = true) }
          }
          .addTo(disposables)
    }

    val repeatSummary = fragmentView.findViewById<TextView>(R.id.details_repeat_summary)

    observeEditor { value -> repeatSummary.text = value.daysOfWeek.summary(requireContext()) }
  }

  private fun onCreateDeleteOnDismissView() {
    val mDeleteOnDismissRow by lazy {
      fragmentView.findViewById(R.id.details_delete_on_dismiss_row) as LinearLayout
    }

    val mDeleteOnDismissCheckBox by lazy {
      fragmentView.findViewById(R.id.details_delete_on_dismiss_checkbox) as CheckBox
    }

    mDeleteOnDismissRow.setOnClickListener {
      modify("Delete on Dismiss") { value ->
        value.copy(isDeleteAfterDismiss = !value.isDeleteAfterDismiss, isEnabled = true)
      }
    }

    observeEditor { value ->
      mDeleteOnDismissCheckBox.isChecked = value.isDeleteAfterDismiss
      mDeleteOnDismissRow.visibility = if (value.daysOfWeek.isRepeatSet) View.GONE else View.VISIBLE
    }
  }

  private fun onCreatePrealarmView() {
    val mPreAlarmRow by lazy {
      fragmentView.findViewById(R.id.details_prealarm_row) as LinearLayout
    }

    val mPreAlarmCheckBox by lazy {
      fragmentView.findViewById(R.id.details_prealarm_checkbox) as CheckBox
    }

    // pre-alarm
    mPreAlarmRow.setOnClickListener {
      modify("Pre-alarm") { value -> value.copy(isPrealarm = !value.isPrealarm, isEnabled = true) }
    }

    observeEditor { value -> mPreAlarmCheckBox.isChecked = value.isPrealarm }

    // pre-alarm duration, if set to "none", remove the option
    prefs.preAlarmDuration
        .observe()
        .subscribe { value ->
          mPreAlarmRow.visibility = if (value.toInt() == -1) View.GONE else View.VISIBLE
        }
        .addTo(disposables)
  }

  private fun onCreateRingtoneView() {
    fragmentView.findViewById<LinearLayout>(R.id.details_ringtone_row).setOnClickListener {
      editor.takeFirst { value ->
        try {
          val pickerIntent =
              Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    value.alarmtone.ringtoneManagerString())

                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))

                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
              }
          startActivityForResult(pickerIntent, ringtonePickerRequestCode)
        } catch (e: Exception) {
          Toast.makeText(
                  context,
                  requireContext().getString(R.string.details_no_ringtone_picker),
                  Toast.LENGTH_LONG)
              .show()
        }
      }
    }

    val ringtoneSummary by lazy {
      fragmentView.findViewById<TextView>(R.id.details_ringtone_summary)
    }
    editor
        .distinctUntilChanged()
        .observeOn(Schedulers.computation())
        .map { value ->
          when (value.alarmtone) {
            is Alarmtone.Silent -> requireContext().getText(R.string.silent_alarm_summary)
            is Alarmtone.Default ->
                RingtoneManager.getRingtone(
                        context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                    .title()
            is Alarmtone.Sound ->
                RingtoneManager.getRingtone(context, Uri.parse(value.alarmtone.uriString)).title()
          }
        }
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { ringtoneSummary.text = it }
        .addTo(disposables)
  }

  private fun onCreatePenaltyView() {
      val mPenalty by lazy {
          fragmentView.findViewById(R.id.penalty) as LinearLayout
      }

      val mPenaltyCheckBox by lazy {
          fragmentView.findViewById(R.id.details_penalty_checkbox) as CheckBox
      }

      // pre-alarm
      mPenalty.setOnClickListener {
          val isPenalty = fragmentView.findViewById<CheckBox>(R.id.details_penalty_checkbox)

          if (isPenalty.isChecked) {
              modify("Penalty-time") { value -> value.copy(penaltyTime = 10, isEnabled = true) }
          } else {
              modify("Penalty-time") { value -> value.copy(penaltyTime = -1, isEnabled = false) }
          }
      }
  }

  // 미션을 위한 전화번호 얻기 함수
  @SuppressLint("Range")
  private fun getPhoneNumber(): String? {
      val names: MutableList<Contact> = arrayListOf()
      val cr = context?.contentResolver
      val cur = cr?.query(
          ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
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

  // 미션: 메시지 보내기
  fun submitMessageMission() {
      val telNumber = getPhoneNumber()
      val intentSent: Intent = Intent("SMS_SENT_ACTION")
      val intentDelivery: Intent = Intent("SMS_DELIVERED_ACTION")
      val sentIntent = PendingIntent.getBroadcast(context, 0, intentSent, 0)
      val deliveredIntent = PendingIntent.getBroadcast(context, 0, intentDelivery, 0)

      context?.registerReceiver(object: BroadcastReceiver() {
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
      context?.registerReceiver(object: BroadcastReceiver() {
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

  private fun onCreateTopRowView() =
      rowHolder.apply {
        daysOfWeek.visibility = View.INVISIBLE
        label.visibility = View.INVISIBLE

        lollipop {
          digitalClock.transitionName = "clock$alarmId"
          container.transitionName = "onOff$alarmId"
          detailsButton.transitionName = "detailsButton$alarmId"
        }

        digitalClock.setLive(false)

        val pickerClickTarget =
            if (layout == Layout.CLASSIC) digitalClockContainer else digitalClock

        container.setOnClickListener {
          modify("onOff") { value -> value.copy(isEnabled = !value.isEnabled) }
        }

        pickerClickTarget.setOnClickListener { showTimePicker() }

        rowView.setOnClickListener { saveAlarm() }

        observeEditor { value ->
          rowHolder.digitalClock.updateTime(
              Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, value.hour)
                set(Calendar.MINUTE, value.minutes)
              })

          rowHolder.onOff.isChecked = value.isEnabled

          highlighter?.applyTo(rowHolder, value.isEnabled)
        }

        animateCheck(check = true)
      }

  override fun onDestroyView() {
    super.onDestroyView()
    disposables.dispose()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (data != null && requestCode == ringtonePickerRequestCode) {
      handlerRingtonePickerResult(data)
    }
  }

  private fun handlerRingtonePickerResult(data: Intent) {
    val alert: String? =
        data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString()

    logger.debug { "Got ringtone: $alert" }

    val alarmtone =
        when (alert) {
          null -> Alarmtone.Silent
          RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString() -> Alarmtone.Default
          else -> Alarmtone.Sound(alert)
        }

    logger.debug { "onActivityResult $alert -> $alarmtone" }

    checkPermissions(requireActivity(), listOf(alarmtone))

    modify("Ringtone picker") { prev -> prev.copy(alarmtone = alarmtone, isEnabled = true) }
  }

  fun Ringtone?.title(): CharSequence {
    return try {
      context?.let { this?.getTitle(it) } ?: context?.getText(R.string.silent_alarm_summary)
    } catch (e: Exception) {
      context?.getText(R.string.silent_alarm_summary)
    } catch (e: NullPointerException) {
      null
    } ?: ""
  }

  override fun onResume() {
    super.onResume()
    backButtonSub = store.onBackPressed().subscribe { saveAlarm() }
  }

  override fun onPause() {
    super.onPause()
    disposableDialog.dispose()
    backButtonSub.dispose()
  }

  private fun saveAlarm() {
    editor.takeFirst { value ->
      alarms.getAlarm(alarmId)?.run { edit { withChangeData(value) } }
      store.hideDetails(rowHolder)
      rowHolder.animateCheck(check = false)
    }
  }

  private fun revert() {
    store.editing().value?.let { edited ->
      // "Revert" on a newly created alarm should delete it.
      if (edited.isNew) {
        alarms.getAlarm(edited.id)?.delete()
      }
      // else do not save changes
      store.hideDetails(rowHolder)
      rowHolder.animateCheck(check = false)
    }
  }

  private fun showTimePicker() {
    disposableDialog =
        TimePickerDialogFragment.showTimePicker(alarmsListActivity.supportFragmentManager)
            .subscribe { picked: Optional<PickedTime> ->
              if (picked.isPresent()) {
                modify("Picker") { value ->
                  value.copy(
                      hour = picked.get().hour, minutes = picked.get().minute, isEnabled = true)
                }
              }
            }
  }

  private fun modify(reason: String, function: (AlarmValue) -> AlarmValue) {
    logger.debug { "Performing modification because of $reason" }
    store.editing().modify { copy(value = value.map { function(it) }) }
  }

  private fun Disposable.addTo(disposables: CompositeDisposable) {
    disposables.add(this)
  }

  private fun RowHolder.animateCheck(check: Boolean) {
    rowHolder.detailsCheckImageView.animate().alpha(if (check) 1f else 0f).setDuration(500).start()
    rowHolder.detailsImageView.animate().alpha(if (check) 0f else 1f).setDuration(500).start()
  }

  private fun observeEditor(block: (value: AlarmValue) -> Unit) {
    editor.distinctUntilChanged().subscribe { block(it) }.addTo(disposables)
  }

  private fun <T : Any> Observable<T>.takeFirst(block: (value: T) -> Unit) {
    take(1).subscribe { block(it) }.addTo(disposables)
  }

  data class Contact(
    val id : String ,
    val name : String,
    val number : String)
}
