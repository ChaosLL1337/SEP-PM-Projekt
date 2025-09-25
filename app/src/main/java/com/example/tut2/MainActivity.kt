package com.example.tut2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.ImageButton
import androidx.annotation.RequiresPermission
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var startScreen: View
    private lateinit var chatScreen: View
    private lateinit var etIn: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var chatRecycler: RecyclerView
    private lateinit var btnBack: ImageButton
    private lateinit var btnAudio: ImageButton
    private val conversation = mutableListOf<Message>()
    private lateinit var chatAdapter: ChatAdapter

    external fun transcribeAudio(pcmPath: String, modelPath: String): String

    init {
        System.loadLibrary("whisper_bridge") // lädt libwhisper.so
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startScreen = findViewById(R.id.startScreen)
        chatScreen = findViewById(R.id.chatScreen)

        etIn = findViewById(R.id.etIn)
        btnSend = findViewById(R.id.btnSend)

        chatRecycler = findViewById(R.id.chatRecycler)

        btnBack = findViewById(R.id.btnBack)

        btnAudio = findViewById(R.id.btnAudio)

        chatAdapter = ChatAdapter(conversation)
        chatRecycler.adapter = chatAdapter
        chatRecycler.layoutManager = LinearLayoutManager(this)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }

        copyModelIfNeeded()


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

                // Neue Konversation starten
                conversation.clear()
                chatAdapter.notifyDataSetChanged() // Adapter leeren

                // Screen wechseln
                startScreen.visibility = View.GONE
                chatScreen.visibility = View.VISIBLE

                // Erste Nachricht hinzufügen
                conversation.add(Message("User", userMsg))
                val botReply = "Ich bin computer-generiert."
                conversation.add(Message("Bot", botReply))

                chatAdapter.notifyDataSetChanged()
                chatRecycler.scrollToPosition(conversation.size - 1)
            }
        }


//        chatSend.setOnClickListener {
//            val userMsg = chatInput.text.toString().trim()
//            if (userMsg.isNotEmpty()) {
//                conversation.add(Message("User", userMsg))
//                chatInput.text.clear()
//
//                val botReply = "Antwort auf: $userMsg"
//                conversation.add(Message("Bot", botReply))
//
//                chatAdapter.notifyDataSetChanged()
//                chatRecycler.scrollToPosition(conversation.size - 1)
//            }
//        }

        btnBack.setOnClickListener {
            chatScreen.visibility = View.GONE
            startScreen.visibility = View.VISIBLE
            etIn.text.clear()
        }

        btnAudio.setOnClickListener {
            // Mic-Permission simpel checken
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
                return@setOnClickListener
            }

            val pcmFile = File(cacheDir, "recording.pcm")
            val modelFile = File(filesDir, "ggml-tiny.bin")

            btnAudio.isSelected = true  // -> grün

            Thread {
                try {
                    // 5s aufnehmen (blockiert jetzt im Hintergrund-Thread)
                    recordPcmToFile(pcmFile)

                    // Whisper-Transkription (nativer Call) – ebenfalls im Hintergrund
                    val result = transcribeAudio(pcmFile.absolutePath, modelFile.absolutePath)

                    runOnUiThread {
                        etIn.setText(result)
                    }
                } finally {
                    runOnUiThread { btnAudio.isSelected = false } // -> wieder weiß
                }
            }.start()
        }
    }


    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun recordPcmToFile(file: File) {
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate / 2) // etwas großzügiger puffer

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, // besseres AGC/NS Profil
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val data = ByteArray(bufferSize)
        val recordMillis = 5_000L

        FileOutputStream(file).use { fos ->
            audioRecord.startRecording()
            val end = System.currentTimeMillis() + recordMillis
            while (System.currentTimeMillis() < end) {
                val read = audioRecord.read(data, 0, data.size)
                if (read > 0) fos.write(data, 0, read)
            }
            audioRecord.stop()
            audioRecord.release()
        }
    }

    fun copyModelIfNeeded() {
        val modelFile = File(filesDir, "ggml-tiny.bin")
        if (!modelFile.exists()) {
            assets.open("ggml-tiny.bin").use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private suspend fun ensureModelFile(context: Context, assetPath: String = "ggml-tiny.bin"): File {
        val out = File(context.filesDir, assetPath.substringAfterLast('/'))
        if (out.exists() && out.length() > 0L) return out

        withContext(Dispatchers.IO) {
            out.outputStream().use { fos ->
                context.assets.open(assetPath, AssetManager.ACCESS_STREAMING).use { ins ->
                    ins.copyTo(fos)
                }
            }
        }
        return out
    }



    private fun appendMessage(sender: String, message: String) {
        // Neue Nachricht zur Liste hinzufügen
        conversation.add(Message(sender, message))

        // Adapter benachrichtigen, dass sich Daten geändert haben
        chatAdapter.notifyItemInserted(conversation.size - 1)

        // Scrollen zum letzten Eintrag
        chatRecycler.scrollToPosition(conversation.size - 1)
    }



//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        outState.putString("conversation", conversation.toString())
//        outState.putString("input", etIn.text.toString())
//    }
}