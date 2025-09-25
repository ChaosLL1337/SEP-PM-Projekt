package com.example.tut2

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var startScreen: View
    private lateinit var chatScreen: View
    private lateinit var etIn: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var chatRecycler: RecyclerView
    private lateinit var btnBack: ImageButton
    private val conversation = mutableListOf<Message>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var btnAudio: ImageButton

    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var outputFile: File? = null

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        startScreen = findViewById(R.id.startScreen)
        chatScreen = findViewById(R.id.chatScreen)

        etIn = findViewById(R.id.etIn)
        btnSend = findViewById(R.id.btnSend)
        btnAudio = findViewById(R.id.btnAudio)
        chatRecycler = findViewById(R.id.chatRecycler)

        btnBack = findViewById(R.id.btnBack)

        chatAdapter = ChatAdapter(conversation)
        chatRecycler.adapter = chatAdapter
        chatRecycler.layoutManager = LinearLayoutManager(this)




        if (savedInstanceState != null) {
            val savedConversation = savedInstanceState.getStringArrayList("conversation") ?: arrayListOf()
            savedConversation.forEach {
                conversation.add(Message("User", it)) // immer als User, Bot geht verloren
            }
            chatAdapter.notifyDataSetChanged()
            chatRecycler.scrollToPosition(conversation.size - 1)

            etIn.setText(savedInstanceState.getString("input", ""))
        }



        btnSend.setOnClickListener {
            val userMsg = etIn.text.toString().trim()
            if (userMsg.isNotEmpty()) {
                conversation.clear()
                chatAdapter.notifyDataSetChanged()
                startScreen.visibility = View.GONE
                chatScreen.visibility = View.VISIBLE
                conversation.add(Message("User", userMsg))
                val botReply = "Ich bin computer-generiert."
                conversation.add(Message("Bot", botReply))
                chatAdapter.notifyDataSetChanged()
                chatRecycler.scrollToPosition(conversation.size - 1)
            }
        }



        btnBack.setOnClickListener {
            chatScreen.visibility = View.GONE
            startScreen.visibility = View.VISIBLE
            etIn.text.clear()
        }


        btnAudio.setOnClickListener {
            if (!isRecording) {
                if (checkPermissions()) {
                    @SuppressLint("MissingPermission")
                    startRecording()
                } else {
                    requestPermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
            } else {
                stopRecordingAndTranscribe()
            }
        }
    }


    private val requestPermissionsLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
            if (granted) {
                @SuppressLint("MissingPermission")
                startRecording()
            }
        }


    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }


    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startRecording() {
        etIn.setText("[audio gestartet]")
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            android.media.MediaRecorder.AudioSource.MIC,
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        // Datei vorbereiten (raw PCM)
        outputFile = File(filesDir, "recording.pcm")
        if (outputFile!!.exists()) outputFile!!.delete()

        val outputStream = outputFile!!.outputStream()

        isRecording = true
        audioRecord?.startRecording()

        // Aufnahme in eigenem Thread
        Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                }
            }
            outputStream.close()
        }.start()
    }



    @SuppressLint("NotifyDataSetChanged")
    private fun stopRecordingAndTranscribe() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // Pfad zur Audio-Datei
        val audioPath = outputFile?.absolutePath ?: return

        // Hier später Whisper aufrufen (JNI)
        // Beispiel:
        // val modelFile = File(filesDir, "ggml-base.bin")
        // val transcription = transcribeAudio(audioPath, modelFile.absolutePath)

        etIn.setText("[Audio beendet]")


    }
}