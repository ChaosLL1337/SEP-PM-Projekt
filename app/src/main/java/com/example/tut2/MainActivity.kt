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
import org.json.JSONArray
import java.io.File
import kotlin.math.log10
import kotlin.math.sqrt

// Retrofit: IMPORTS NICHT VERGESSEN
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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

    @Volatile private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var outputFile: File? = null
    private var recordingThread: Thread? = null
    private var pcmOut: java.io.FileOutputStream? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
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

        // Senden
        btnSend.setOnClickListener {
            val userMsg = etIn.text.toString().trim()
            if (userMsg.isEmpty()) return@setOnClickListener

            startScreen.visibility = View.GONE
            chatScreen.visibility = View.VISIBLE

            conversation.add(Message("User", userMsg))
            etIn.text.clear()
            chatAdapter.notifyDataSetChanged()
            chatRecycler.scrollToPosition(conversation.size - 1)

            RetrofitClient.instance.sendText(InputData(userMsg))
                .enqueue(object : Callback<ResponseData> {
                    override fun onResponse(
                        call: Call<ResponseData>,
                        response: Response<ResponseData>
                    ) {
                        val reply = if (response.isSuccessful) {
                            response.body()?.response ?: "Fehler: keine Antwort"
                        } else {
                            "Fehlercode: ${response.code()}"
                        }
                        conversation.add(Message("Bot", reply))
                        chatAdapter.notifyDataSetChanged()
                        chatRecycler.scrollToPosition(conversation.size - 1)
                    }

                    override fun onFailure(call: Call<ResponseData>, t: Throwable) {
                        conversation.add(Message("Bot", "Fehler: ${t.message}"))
                        chatAdapter.notifyDataSetChanged()
                        chatRecycler.scrollToPosition(conversation.size - 1)
                    }
                })
        }

        btnBack.setOnClickListener {
            chatScreen.visibility = View.GONE
            startScreen.visibility = View.VISIBLE
            etIn.text.clear()
        }

        // Mic-Button
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
                stopRecordingAndTranscribeAsync()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        try { audioRecord?.stop() } catch (_: Throwable) {}
        try { audioRecord?.release() } catch (_: Throwable) {}
        audioRecord = null
        try { recordingThread?.join(300) } catch (_: InterruptedException) {}
        recordingThread = null
        try { pcmOut?.close() } catch (_: Throwable) {}
        pcmOut = null
    }

    private fun checkPermissions() =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startRecording() {
        // *** Letzte Konversation löschen ***
        conversation.clear()
        chatAdapter.notifyDataSetChanged()
        etIn.setText("")

        etIn.setText("[Aufnahme gestartet – tippe erneut zum Stoppen]")
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
            try { pcmOut?.close() } catch (_: Throwable) {}
            pcmOut = null
            etIn.setText("Fehler beim Starten der Aufnahme: ${t.message}")
            return
        }

        recordingThread = Thread {
            val buffer = ByteArray(minBuffer)
            try {
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        pcmOut?.write(buffer, 0, read)
                    } else if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                        read == AudioRecord.ERROR_BAD_VALUE) {
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

    private fun stopRecordingAndTranscribeAsync() {
        isRecording = false
        try { if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) audioRecord?.stop() } catch (_: Throwable) {}
        try { audioRecord?.release() } catch (_: Throwable) {}
        audioRecord = null
        try { recordingThread?.join(1500) } catch (_: InterruptedException) {}
        recordingThread = null

        Thread {
            val ui = { block: () -> Unit -> runOnUiThread(block) }

            val pcm = outputFile
            if (pcm == null || !pcm.exists() || pcm.length() < 320) {
                ui {
                    etIn.setText("Keine gültige PCM-Datei gefunden")
                    btnAudio.isEnabled = true
                }
                return@Thread
            }

            val wavFile = File(filesDir, "recording.wav")
            try {
                AudioUtil.pcmToWav(pcm, wavFile, 16000, 1, 16)
            } catch (e: Exception) {
                ui {
                    etIn.setText("Fehler bei PCM→WAV: ${e.message}")
                    btnAudio.isEnabled = true
                }
                return@Thread
            }

            val modelFile = try { copyAssetToFiles("models/ggml-tiny.bin") } catch (e: Exception) {
                ui {
                    etIn.setText("Modellkopie fehlgeschlagen: ${e.message}")
                    btnAudio.isEnabled = true
                }
                return@Thread
            }

            // *** WICHTIG: Segmente abrufen, nicht Plain-Text! ***
            val jsonStr = try {
                WhisperBridge.transcribeWavSegments(modelFile.absolutePath, wavFile.absolutePath, "de")
            } catch (e: Throwable) {
                ui {
                    etIn.setText("Whisper-Fehler: ${e.javaClass.simpleName}: ${e.message}")
                    btnAudio.isEnabled = true
                }
                return@Thread
            }

            // JSON → Segmente
            val segs = mutableListOf<Segment>()
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    segs.add(Segment(o.getLong("t0_ms"), o.getLong("t1_ms"), o.getString("text")))
                }
            } catch (e: Exception) {
                ui {
                    etIn.setText("Segment-Parsing-Fehler: ${e.message}\nAntwort war: $jsonStr")
                    btnAudio.isEnabled = true
                }
                return@Thread
            }

            // 1) kurze Pausen zusammenkleben (z. B. < 120 ms)
            val utterances = groupIntoUtterances(segs, gapMs = 120L)

            // 2) *** neue Zeile, wenn Gap >= 200 ms ***
            val text = buildString {
                var lastEnd = -1L
                for ((idx, u) in utterances.withIndex()) {
                    if (idx > 0) {
                        val gap = u.t0_ms - lastEnd
                        if (gap >= 200L) append('\n') else append(' ')
                    }
                    append(u.text.trim())
                    lastEnd = u.t1_ms
                }
            }.ifBlank { "[whisper] Kein Text erkannt" }

            ui {
                etIn.setText(text)
                btnAudio.isEnabled = true
            }
        }.start()
    }

    private fun copyAssetToFiles(assetPath: String, overwrite: Boolean = false): File {
        val outFile = File(filesDir, assetPath.substringAfterLast('/'))
        if (outFile.exists() && !overwrite) return outFile
        assets.open(assetPath).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        return outFile
    }

    // ---------- Datenklassen & einfache Gruppierung ----------
    data class Segment(val t0_ms: Long, val t1_ms: Long, val text: String)
    data class Utterance(var t0_ms: Long, var t1_ms: Long, var text: String)

    /** Merged aufeinanderfolgende Segmente, wenn Pause < gapMs */
    private fun groupIntoUtterances(segments: List<Segment>, gapMs: Long): List<Utterance> {
        if (segments.isEmpty()) return emptyList()
        val out = mutableListOf<Utterance>()
        var cur = Utterance(segments[0].t0_ms, segments[0].t1_ms, segments[0].text)
        for (i in 1 until segments.size) {
            val s = segments[i]
            val gap = s.t0_ms - segments[i - 1].t1_ms
            if (gap < gapMs) {
                cur.t1_ms = s.t1_ms
                cur.text = (cur.text + " " + s.text).trim()
            } else {
                out.add(cur)
                cur = Utterance(s.t0_ms, s.t1_ms, s.text)
            }
        }
        out.add(cur)
        return out
    }

    // nur falls du später RMS/Noise brauchst
    private fun rmsDb(samples: ShortArray): Float {
        if (samples.isEmpty()) return -120f
        var sum = 0.0
        for (s in samples) sum += (s * s).toDouble()
        val mean = sum / samples.size
        val rms = sqrt(mean)
        val db = 20 * log10((rms / Short.MAX_VALUE).coerceAtMost(1.0))
        return if (db.isFinite()) db.toFloat() else -120f
    }
}
