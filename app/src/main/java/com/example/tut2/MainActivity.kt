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
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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

    private var sessionOffsetMs: Long = 0

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

        btnSend.setOnClickListener {
            val userMsg = etIn.text.toString().trim()
            if (userMsg.isNotEmpty()) {
                startScreen.visibility = View.GONE
                chatScreen.visibility = View.VISIBLE
                conversation.add(Message("User", userMsg))
                etIn.text.clear()
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

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startRecording() {
        etIn.setText("[Aufnahme gestartet – tippe erneut zum Stoppen]")
        btnAudio.isEnabled = true

        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
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
            return
        }

        outputFile = File(filesDir, "recording.pcm").apply { if (exists()) delete() }
        pcmOut = outputFile!!.outputStream()

        isRecording = true
        audioRecord?.startRecording()

        recordingThread = Thread {
            val buffer = ByteArray(minBuffer)
            try {
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) pcmOut?.write(buffer, 0, read)
                }
            } finally {
                try { pcmOut?.flush() } catch (_: Throwable) {}
                try { pcmOut?.close() } catch (_: Throwable) {}
                pcmOut = null
            }
        }.also { it.start() }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun stopRecordingAndTranscribeAsync() {
        isRecording = false
        try { audioRecord?.stop() } catch (_: Throwable) {}
        try { audioRecord?.release() } catch (_: Throwable) {}
        audioRecord = null
        try { recordingThread?.join(1500) } catch (_: Throwable) {}
        recordingThread = null

        Thread {
            val pcm = outputFile
            if (pcm == null || !pcm.exists()) {
                runOnUiThread { etIn.setText("Keine PCM-Datei gefunden") }
                return@Thread
            }

            val wavFile = File(filesDir, "recording.wav")
            try {
                AudioUtil.pcmToWav(pcm, wavFile, 16000, 1, 16)
            } catch (e: Exception) {
                runOnUiThread { etIn.setText("Fehler PCM→WAV: ${e.message}") }
                return@Thread
            }

            val modelFile = copyAssetToFiles("models/ggml-tiny.bin")

            // Segmente als JSON vom JNI holen
            val jsonStr = WhisperBridge.transcribeWavSegments(
                modelFile.absolutePath, wavFile.absolutePath, "de"
            )
            val arr = JSONArray(jsonStr)
            val segments = mutableListOf<Utterance>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                segments.add(
                    Utterance(
                        o.getLong("t0_ms"),
                        o.getLong("t1_ms"),
                        o.getString("text")
                    )
                )
            }

            assignSpeakersForUtterances(segments, wavFile, 16000)

            // Render mit Absätzen
            val sb = StringBuilder()
            var lastSpk = -1
            for (u in segments) {
                if (lastSpk != -1 && u.speaker != lastSpk) sb.append("\n")
                val tag = when (u.speaker) { 0 -> "Sprecher 1"; 1 -> "Sprecher 2"; else -> "?" }
                sb.append("[$tag] ").append(u.text.trim()).append("\n")
                lastSpk = u.speaker
            }

            runOnUiThread {
                etIn.setText(sb.toString().trim())
                btnAudio.isEnabled = true
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
            }
        }
        return outFile
    }

    // --- Speaker Assignment ---
    data class Utterance(val t0_ms: Long, val t1_ms: Long, val text: String, var speaker: Int = -1)

    private fun assignSpeakersForUtterances(
        utts: List<Utterance>,
        wavFile: File,
        sampleRate: Int,
        pauseMsThreshold: Long = 200L,
        dbDeltaThreshold: Float = 6f
    ) {
        if (utts.isEmpty()) return
        val pcm = readPcm16FromWav(wavFile) ?: return

        fun slice(startMs: Long, endMs: Long): ShortArray {
            val i0 = ((startMs * sampleRate) / 1000).toInt().coerceIn(0, pcm.size)
            val i1 = ((endMs * sampleRate) / 1000).toInt().coerceIn(i0, pcm.size)
            return pcm.copyOfRange(i0, i1)
        }

        val levels = utts.map { rmsDb(slice(it.t0_ms, it.t1_ms)) }
        var spk = 0
        utts[0].speaker = spk
        var prevDb = levels[0]

        for (i in 1 until utts.size) {
            val prev = utts[i - 1]
            val cur = utts[i]
            val gap = cur.t0_ms - prev.t1_ms
            val db = levels[i]
            if (gap >= pauseMsThreshold || abs(db - prevDb) >= dbDeltaThreshold) {
                spk = 1 - spk
            }
            cur.speaker = spk
            prevDb = db
        }
    }

    private fun rmsDb(samples: ShortArray): Float {
        if (samples.isEmpty()) return -120f
        var sum = 0.0
        for (s in samples) sum += (s * s).toDouble()
        val mean = sum / samples.size
        val rms = sqrt(mean)
        val db = 20 * log10((rms / Short.MAX_VALUE).coerceAtMost(1.0))
        return if (db.isFinite()) db.toFloat() else -120f
    }

    private fun readPcm16FromWav(file: File): ShortArray? {
        val bytes = file.readBytes()
        if (bytes.size < 44) return null
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val sz = toIntLE(bytes, pos + 4)
            pos += 8
            if (id == "data") {
                val dataStart = pos
                val dataEnd = (pos + sz).coerceAtMost(bytes.size)
                val pcmBytes = bytes.copyOfRange(dataStart, dataEnd)
                val bb = java.nio.ByteBuffer.wrap(pcmBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                val out = ShortArray(pcmBytes.size / 2)
                bb.asShortBuffer().get(out)
                return out
            } else {
                pos += sz
            }
        }
        return null
    }

    private fun toIntLE(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xff) or
                ((b[off + 1].toInt() and 0xff) shl 8) or
                ((b[off + 2].toInt() and 0xff) shl 16) or
                ((b[off + 3].toInt() and 0xff) shl 24)
    }
}
