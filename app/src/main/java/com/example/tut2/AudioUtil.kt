import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object AudioUtil {

    /**
     * Konvertiert eine RAW-PCM-Datei (16 kHz, 16-bit, mono) in eine WAV-Datei mit gültigem Header.
     */
    @Throws(IOException::class)
    fun pcmToWav(
        pcmFile: File,
        wavFile: File,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ) {
        require(bitsPerSample == 16) { "Nur 16-bit PCM wird hier unterstützt." }
        require(channels == 1) { "Nur Mono wird hier unterstützt." }

        val totalAudioLen = pcmFile.length() // nur PCM-Nutzdaten
        // einfache Sicherheitsprüfung gegen 32-bit-Überlauf im Header
        require(totalAudioLen <= Int.MAX_VALUE - 44) { "Audio zu groß für 32-bit WAV-Header." }

        val totalDataLen = totalAudioLen + 36 // Daten + Header-Teil
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()

        FileInputStream(pcmFile).use { inStream ->
            FileOutputStream(wavFile).use { outStream ->
                // 44-Byte WAV-Header schreiben
                writeWavHeader(
                    outStream,
                    totalAudioLen = totalAudioLen.toInt(),
                    totalDataLen  = totalDataLen.toInt(),
                    sampleRate    = sampleRate,
                    channels      = channels.toShort(),
                    byteRate      = byteRate,
                    blockAlign    = blockAlign,
                    bitsPerSample = bitsPerSample.toShort()
                )

                // PCM-Daten kopieren
                val buffer = ByteArray(32 * 1024)
                var read: Int
                while (inStream.read(buffer).also { read = it } != -1) {
                    outStream.write(buffer, 0, read)
                }
            }
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Int,
        totalDataLen: Int,
        sampleRate: Int,
        channels: Short,
        byteRate: Int,
        blockAlign: Short,
        bitsPerSample: Short
    ) {
        val header = ByteArray(44)

        // ChunkID "RIFF"
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // ChunkSize = 36 + Subchunk2Size (klein-endian)
        writeIntLE(header, 4, totalDataLen)

        // Format "WAVE"
        header[8]  = 'W'.code.toByte()
        header[9]  = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // Subchunk1ID "fmt "
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        // Subchunk1Size = 16 für PCM
        writeIntLE(header, 16, 16)

        // AudioFormat = 1 (PCM)
        writeShortLE(header, 20, 1)

        // NumChannels
        writeShortLE(header, 22, channels)

        // SampleRate
        writeIntLE(header, 24, sampleRate)

        // ByteRate
        writeIntLE(header, 28, byteRate)

        // BlockAlign
        writeShortLE(header, 32, blockAlign)

        // BitsPerSample
        writeShortLE(header, 34, bitsPerSample)

        // Subchunk2ID "data"
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        // Subchunk2Size = totalAudioLen
        writeIntLE(header, 40, totalAudioLen)

        out.write(header, 0, 44)
    }

    private fun writeIntLE(target: ByteArray, offset: Int, value: Int) {
        target[offset]     = ( value        and 0xff).toByte()
        target[offset + 1] = ((value shr 8) and 0xff).toByte()
        target[offset + 2] = ((value shr 16)and 0xff).toByte()
        target[offset + 3] = ((value shr 24)and 0xff).toByte()
    }

    private fun writeShortLE(target: ByteArray, offset: Int, value: Short) {
        val v = value.toInt()
        target[offset]     = ( v        and 0xff).toByte()
        target[offset + 1] = ((v shr 8) and 0xff).toByte()
    }
}
