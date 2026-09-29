package yos.music.player.code

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.kyant.taglib.AudioPropertiesReadStyle
import com.kyant.taglib.TagLib
import yos.music.player.code.utils.lrc.YosTtmlFactory
import java.io.File
import java.nio.charset.Charset

object AudioMetadataUtils {
    private val embeddedLyricKeys = listOf(
        "LYRICS", "LYRICSENG", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "USLT", "SYLT"
    )
    private val lrcTimestamp = Regex("""\[\d{1,3}:\d{2}""")

    // Approach adapted from shouryadixitisverycool/Flamingo
    fun loadEmbeddedLyrics(filePath: String): String? {
        return try {
            ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val props = TagLib.getMetadata(fd.dup().detachFd(), false)?.propertyMap
                    ?: return@use null
                embeddedLyricKeys.firstNotNullOfOrNull { key ->
                    props[key]?.firstOrNull { lrcTimestamp.containsMatchIn(it) || YosTtmlFactory.isTtml(it) }?.trim()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadTtmlFile(audioPath: String): String? {
        val file = File("${audioPath.substringBeforeLast(".")}.ttml")
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun loadLrcFile(context: Context, filePath: String): String? {
        return try {
            val file = File(filePath)
            val uri = Uri.fromFile(file)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes().toString(Charset.defaultCharset())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getQualityInfos(filePath: String): Pair<Int, Int> {
        val songFile = File(filePath)
        var bitrate: Int
        var sampleRate: Int

        println("质量分析 Taglib 实现获取")

        ParcelFileDescriptor.open(songFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val audioProperties = TagLib.getAudioProperties(fd.dup().detachFd(), AudioPropertiesReadStyle.Fast)
            bitrate = audioProperties?.bitrate ?: -1
            sampleRate = audioProperties?.sampleRate ?: -1
        }

        if (bitrate == -1 || sampleRate == -1) {
            val extractor = MediaExtractor()
            try {
                println("质量分析 MediaExtractor 实现获取")
                extractor.setDataSource(filePath)
                val format = extractor.getTrackFormat(0)
                if (bitrate == -1) {
                    bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                }
                if (sampleRate == -1) {
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                extractor.release()
            }
        }

        return Pair(bitrate, sampleRate)
    }

}