package lilcode.aop.p3.c03.alarm

import android.R
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import lilcode.aop.p3.c03.alarm.databinding.MusicDialogBinding
import java.io.File
import java.text.SimpleDateFormat

class MusicDialog(private val context: AppCompatActivity) {
    private lateinit var binding: MusicDialogBinding
    private val dlg = Dialog(context)
    private lateinit var listener : MyDialogOKClickedListener

    lateinit var mp3List: ArrayList<String>    //mp3파일을 저장할 리스트
    lateinit var selectedMp3: String //현재 선택된 mp3파일
    var mp3Path = { MainActivity.applicationContext(). }

    lateinit var mPlayer: MediaPlayer    //mp3 player 객체 생성

    fun show(content: String) {
        binding = MusicDialogBinding.inflate(context.layoutInflater)

        //dlg.requestWindowFeature(Window.FEATURE_NO_TITLE) // 타이블 바 제거
        dlg.setContentView(binding.root)     //다이얼로그에 사용할 xml 파일을 불러옴

        mp3List = ArrayList()   //mp3파일을 저장할 리스트

        var listFiles = File(mp3Path).listFiles()   //해당 경로에 있는 모든 파일들을 File[] 타입 변수에 저장 (mp3말고 다른 파일이 있을 수 있음)
        var fileName: String    //파일 전체 이름
        var extName: String     //확장자 이름
        if (listFiles.size == 0) {
            AlertDialog.Builder(context).run {
                setMessage("저장소에 저장된 노래가 존재하지 않습니다")
                show()
            }
            return
        }

        for (file in listFiles!!) {
            fileName = file.name
            extName = fileName.substring(fileName.length - 3)   //확장자 추출하기
            if (extName == "mp3")   //확장자가 mp3일 경우 List에 추가
                mp3List.add(fileName)
        }

        /*어댑터(내용물 or 컨텐츠의 의미) 생성*/
        var ad = ArrayAdapter(context, R.layout.simple_list_item_single_choice, mp3List)

        /*리스트뷰에 생성한 어댑터 지정*/
        binding.listViewMp3.choiceMode = ListView.CHOICE_MODE_SINGLE
        binding.listViewMp3.adapter = ad
        binding.listViewMp3.setItemChecked(0, true)  //초기 선택값 : 인덱스 0번에 위치

        /*아이템이 선택되면 selectedMp3에 값을 넣어줌*/
        binding.listViewMp3.setOnItemClickListener { parent, view, position, id ->  //선택한 아이템의 인덱스 값은 position으로 알 수 있음
            selectedMp3 = mp3List[position]
        }
        selectedMp3 = mp3List[0]        //초기값은 0번째 아이템

        mPlayer = MediaPlayer()

        binding.btnPlay.setOnClickListener {
            mPlayer.setDataSource(mp3Path + selectedMp3)    //경로+파일명
            mPlayer.prepare()
            mPlayer.start()     //음악 재생
            mPlayer.isLooping = false   //반복 재생x
            binding.btnPlay.setTextColor(Color.WHITE)   //실행하면 버튼색이 바뀜
            binding.btnStop.setTextColor(Color.RED)
            binding.textView.text = "실행중인 음악 : $selectedMp3"

            /* 실시간으로 변경되는 진행시간과 시크바를 구현하기 위한 스레드 사용*/
            object : Thread() {
                var timeFormat = SimpleDateFormat("mm:ss")  //"분:초"를 나타낼 수 있도록 포멧팅
                override fun run() {
                    super.run()
                    if (mPlayer == null)
                        return
                    binding.seekBar.max = mPlayer.duration  // mPlayer.duration : 음악 총 시간
                    /*while (mPlayer.isPlaying) {
                        runOnUiThread { //화면의 위젯을 변경할 때 사용 (이 메소드 없이 아래 코드를 추가하면 실행x)
                            binding.seekBar.progress = mPlayer.currentPosition
                            binding.textView2.text = "진행시간 : " + timeFormat.format(mPlayer.currentPosition)
                        }
                        SystemClock.sleep(200)
                    }*/

                    /*1. 음악이 종료되면 자동으로 초기상태로 전환*/
                    /*btnStop.setOnClickListener()와 동일한 코드*/
                    if(!mPlayer.isPlaying){
                        mPlayer.stop()      //음악 정지
                        mPlayer.reset()
                        binding.btnPlay.setTextColor(Color.RED)   //실행하면 버튼색이 바뀜
                        binding.btnStop.setTextColor(Color.WHITE)
                        binding.textView.text = "실행중인 음악 : "
                        binding.seekBar.progress = 0
                        binding.textView2.text = "진행시간 : "
                    }
                }
            }.start()

        }

        binding.btnStop.setOnClickListener {
            mPlayer.stop()      //음악 정지
            mPlayer.reset()
            binding.btnPlay.setTextColor(Color.RED)   //실행하면 버튼색이 바뀜
            binding.btnStop.setTextColor(Color.WHITE)
            binding.textView.text = "실행중인 음악 : "
            binding.seekBar.progress = 0
            binding.textView2.text = "진행시간 : "

        }

        /*2. 시크바로 음악의 해당 부분을 재생*/
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser){
                    mPlayer.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dlg.show()
    }

    fun setOnOKClickedListener(listener: (String) -> Unit) {
        this.listener = object: MyDialogOKClickedListener {
            override fun onOKClicked(content: String) {
                listener(content)
            }
        }
    }

    interface MyDialogOKClickedListener {
        fun onOKClicked(content : String)
    }
}