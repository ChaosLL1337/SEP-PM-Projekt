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

    @Volatile private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var outputFile: File? = null
    private var recordingThread: Thread? = null
    private var pcmOut: java.io.FileOutputStream? = null



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

        try {
            val ok = assets.list("models")?.contains("ggml-tiny.bin") == true
            if (!ok) {
                etIn.setText("Hinweis: assets/models/ggml-tiny.bin fehlt")
            }
        } catch (_: Throwable) { }
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
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer == android.media.AudioRecord.ERROR || minBuffer == android.media.AudioRecord.ERROR_BAD_VALUE) {
            etIn.setText("Fehler: ungültige Buffergröße")
            return
        }

        audioRecord = AudioRecord(
            android.media.MediaRecorder.AudioSource.MIC,
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
            minBuffer
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            etIn.setText("Fehler: AudioRecord nicht initialisiert")
            audioRecord?.release()
            audioRecord = null
            return
        }

        outputFile = File(filesDir, "recording.pcm").apply { if (exists()) delete() }
        pcmOut = outputFile!!.outputStream()

        isRecording = true
        try {
            audioRecord?.startRecording()
        } catch (t: Throwable) {
            isRecording = false
            pcmOut?.close()
            pcmOut = null
            etIn.setText("Fehler beim Starten der Aufnahme: ${t.message}")
            return
        }

        // Aufnahme-Thread
        recordingThread = Thread {
            val buffer = ByteArray(minBuffer)
            try {
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        pcmOut?.write(buffer, 0, read)
                    } else if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                        // kurz warten, um busy loop zu vermeiden
                        try { Thread.sleep(5) } catch (_: InterruptedException) {}
                    }
                }
            } catch (t: Throwable) {
                // Logging optional
            } finally {
                try { pcmOut?.flush() } catch (_: Throwable) {}
                try { pcmOut?.close() } catch (_: Throwable) {}
                pcmOut = null
            }
        }.also { it.start() }
    }




    @SuppressLint("NotifyDataSetChanged")
    private fun stopRecordingAndTranscribe() {
        // Doppelaufrufe abfangen
        if (!isRecording && audioRecord == null) {
            etIn.setText("Keine laufende Aufnahme")
            return
        }

        // 1) Aufnahme sauber stoppen
        isRecording = false
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
        } catch (_: Throwable) { }
        try { audioRecord?.release() } catch (_: Throwable) { }
        audioRecord = null

        // 2) Thread beenden lassen und warten, bis Outputstream zu ist
        try { recordingThread?.join(1500) } catch (_: InterruptedException) { }
        recordingThread = null

        // 3) PCM prüfen
        val pcm = outputFile
        if (pcm == null || !pcm.exists()) {
            etIn.setText("Keine PCM-Datei gefunden")
            return
        }
        if (pcm.length() < 320) { // ~10ms Guard; anpassen wenn nötig
            etIn.setText("PCM-Datei zu klein (${pcm.length()} B)")
            return
        }

        // 4) WAV schreiben
        val wavFile = File(filesDir, "recording.wav")
        try {
            AudioUtil.pcmToWav(
                pcmFile = pcm,
                wavFile = wavFile,
                sampleRate = 16000,
                channels = 1,
                bitsPerSample = 16
            )
        } catch (e: Exception) {
            etIn.setText("Fehler bei PCM→WAV: ${e.javaClass.simpleName}: ${e.message}")
            return
        }

        // 5) Modell aus Assets bereitstellen – vorher existiert es wirklich?
        val hasModel = try {
            assets.list("models")?.contains("ggml-tiny.bin") == true
        } catch (_: Throwable) { false }

        if (!hasModel) {
            etIn.setText("Modell nicht in assets/models/ggml-tiny.bin gefunden")
            return
        }

        val modelFile = try {
            copyAssetToFiles("models/ggml-tiny.bin")
        } catch (e: Exception) {
            etIn.setText("Fehler beim Modellkopieren: ${e.javaClass.simpleName}: ${e.message}")
            return
        }

        // 6) Whisper JNI aufrufen – hart absichern
        val result: String = try {
            // Falls die native Lib nicht geladen ist, knallt es hier:
            WhisperBridge.transcribeWav(
                modelPath = modelFile.absolutePath,
                wavPath   = wavFile.absolutePath,
                lang      = "de"
            )
        } catch (e: UnsatisfiedLinkError) {
            etIn.setText("Native Lib nicht geladen oder ABI falsch: ${e.message}")
            return
        } catch (e: NoSuchMethodError) {
            etIn.setText("Methodensignatur passt nicht zur JNI-Bridge: ${e.message}")
            return
        } catch (e: Exception) {
            etIn.setText("Whisper-Fehler: ${e.javaClass.simpleName}: ${e.message}")
            return
        }

        etIn.setText(result.ifBlank { "[whisper] Kein Text erkannt" })
    }



    private fun copyAssetToFiles(assetPath: String, overwrite: Boolean = false): File {
        val fileName = assetPath.substringAfterLast('/')
        val outFile = File(filesDir, fileName)

        if (outFile.exists() && !overwrite) return outFile

        assets.open(assetPath).use { inStream ->
            outFile.outputStream().use { outStream ->
                val buf = ByteArray(32 * 1024)
                var r: Int
                while (inStream.read(buf).also { r = it } != -1) {
                    outStream.write(buf, 0, r)
                }
                outStream.flush()
            }
        }
        return outFile
    }
}