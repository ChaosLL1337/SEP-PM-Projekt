package com.example.tut2

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
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

    // Permission launcher
    private val requestPermissionsLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
            if (granted) {
                @SuppressLint("MissingPermission")
                startRecording()
            } else {
                etIn.setText("Mikrofonberechtigung verweigert")
            }
        }

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
            savedConversation.forEach { conversation.add(Message("User", it)) } // Bot geht verloren
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

        // Mic-Button: Start ↔ Stop+Transkribieren
        btnAudio.setOnClickListener {
            if (!isRecording) {
                if (checkPermissions()) {
                    @SuppressLint("MissingPermission")
                    startRecording()
                } else {
                    requestPermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
            } else {
                etIn.setText("Stoppe Aufnahme …")
                btnAudio.isEnabled = false
                btnAudio.contentDescription = "Stoppe & transkribiere"
                stopRecordingAndTranscribeAsync() // → im Hintergrund
            }
        }

        // Sanity-Check: Modell vorhanden?
        try {
            val ok = assets.list("models")?.contains("ggml-tiny.bin") == true
            if (!ok) {
                etIn.setText("Hinweis: assets/models/ggml-tiny.bin fehlt")
            }
        } catch (_: Throwable) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Aufräumen, falls Activity während Aufnahme geschlossen wird
        isRecording = false
        try { audioRecord?.stop() } catch (_: Throwable) { }
        try { audioRecord?.release() } catch (_: Throwable) { }
        audioRecord = null
        try { recordingThread?.join(300) } catch (_: InterruptedException) { }
        recordingThread = null
        try { pcmOut?.close() } catch (_: Throwable) { }
        pcmOut = null
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startRecording() {
        etIn.setText("[Aufnahme gestartet – tippe erneut zum Stoppen]")
        btnAudio.isEnabled = true
        btnAudio.contentDescription = "Stopp"

        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
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
            try { pcmOut?.close() } catch (_: Throwable) { }
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
                        try { Thread.sleep(5) } catch (_: InterruptedException) {}
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { pcmOut?.flush() } catch (_: Throwable) {}
                try { pcmOut?.close() } catch (_: Throwable) {}
                pcmOut = null
            }
        }.also { it.start() }
    }

    // ---- Transkription im Hintergrund-Thread ----
    @SuppressLint("NotifyDataSetChanged")
    private fun stopRecordingAndTranscribeAsync() {
        // 1) Aufnahme sauber stoppen (kurz am UI)
        if (!isRecording && audioRecord == null) {
            etIn.setText("Keine laufende Aufnahme")
            btnAudio.isEnabled = true
            btnAudio.contentDescription = "Aufnahme starten"
            return
        }

        isRecording = false
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
        } catch (_: Throwable) { }
        try { audioRecord?.release() } catch (_: Throwable) { }
        audioRecord = null

        try { recordingThread?.join(1500) } catch (_: InterruptedException) { }
        recordingThread = null

        // 2) Schweres Zeug im Worker-Thread
        Thread {
            val ui = { block: () -> Unit -> runOnUiThread(block) }

            val pcm = outputFile
            if (pcm == null || !pcm.exists()) {
                ui {
                    etIn.setText("Keine PCM-Datei gefunden")
                    btnAudio.isEnabled = true
                    btnAudio.contentDescription = "Aufnahme starten"
                }
                return@Thread
            }
            if (pcm.length() < 320) {
                ui {
                    etIn.setText("PCM-Datei zu klein (${pcm.length()} B)")
                    btnAudio.isEnabled = true
                    btnAudio.contentDescription = "Aufnahme starten"
                }
                return@Thread
            }

            ui { etIn.setText("Konvertiere nach WAV …") }

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
                ui {
                    etIn.setText("Fehler bei PCM→WAV: ${e.javaClass.simpleName}: ${e.message}")
                    btnAudio.isEnabled = true
                    btnAudio.contentDescription = "Aufnahme starten"
                }
                return@Thread
            }

            // Modell checken & kopieren
            val hasModel = try {
                assets.list("models")?.contains("ggml-tiny.bin") == true
            } catch (_: Throwable) { false }

            if (!hasModel) {
                ui {
                    etIn.setText("Modell nicht in assets/models/ggml-tiny.bin gefunden")
                    btnAudio.isEnabled = true
                    btnAudio.contentDescription = "Aufnahme starten"
                }
                return@Thread
            }

            val modelFile = try {
                copyAssetToFiles("models/ggml-tiny.bin")
            } catch (e: Exception) {
                ui {
                    etIn.setText("Fehler beim Modellkopieren: ${e.javaClass.simpleName}: ${e.message}")
                    btnAudio.isEnabled = true
                    btnAudio.contentDescription = "Aufnahme starten"
                }
                return@Thread
            }

            ui { etIn.setText("Transkribiere …") }

            val result: String = try {
                WhisperBridge.transcribeWav(
                    modelPath = modelFile.absolutePath,
                    wavPath   = wavFile.absolutePath,
                    lang      = "de"
                )
            } catch (e: UnsatisfiedLinkError) {
                ui {
                    etIn.setText("Native Lib nicht geladen oder ABI falsch: ${e.message}")
                    btnAudio.isEnabled = true
                    btnAudio.contentDescription = "Aufnahme starten"
                }
                return@Thread
            } catch (e: NoSuchMethodError) {
                ui {
                    etIn.setText("Methodensignatur passt nicht zur JNI-Bridge: ${e.message}")
                    btnAudio.isEnabled = true
                    btnAudio.contentDescription = "Aufnahme starten"
                }
                return@Thread
            } catch (e: Exception) {
                ui {
                    etIn.setText("Whisper-Fehler: ${e.javaClass.simpleName}: ${e.message}")
                    btnAudio.isEnabled = true
                    btnAudio.contentDescription = "Aufnahme starten"
                }
                return@Thread
            }

            // 3) Ergebnis ins UI (optional auch in den Chat)
            ui {
                val text = result.ifBlank { "[whisper] Kein Text erkannt" }
                etIn.setText(text)

                // Optional in die Chatliste pushen:
                /*
                startScreen.visibility = View.GONE
                chatScreen.visibility = View.VISIBLE
                conversation.add(Message("User", "[Sprachaufnahme]"))
                conversation.add(Message("Bot", text))
                chatAdapter.notifyDataSetChanged()
                chatRecycler.scrollToPosition(conversation.size - 1)
                */

                btnAudio.isEnabled = true
                btnAudio.contentDescription = "Aufnahme starten"
            }
        }.start()
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
