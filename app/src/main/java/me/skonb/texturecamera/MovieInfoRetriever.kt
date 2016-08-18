package me.skonb.texturecamera

import android.media.MediaMetadataRetriever
import com.joestelmach.natty.Parser
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.regex.Pattern

/**
 * Created by skonb on 2016/08/08.
 */
class MovieInfoRetriever {
    companion object {

        fun retrieve(moviePath: String?): MovieInfo? {
            return moviePath?.let {
                val info = MovieInfo()
                val r = MediaMetadataRetriever()
                r.setDataSource(moviePath)
                val loc = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                val date = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                val width = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val height = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                r.release()
                info.duration = duration.toLong()
                if (loc != null) {
                    val matcher = Pattern.compile("([-+][0-9.]+)([-+][0-9.]+)").matcher(loc)
                    if (matcher.matches()) {
                        val res = matcher.toMatchResult()
                        val lat = res.group(1).toDouble()
                        val lng = res.group(2).toDouble()
                        info.lat = lat
                        info.lng = lng
                    }
                }
                if (date != null) {
                    try {
                        val parser = Parser()
                        val groups = parser.parse(date)
                        info.takenDate = groups.first().dates.first()
                    } catch(e: ParseException) {
                        e.printStackTrace()
                    }
                }
                info.rotation = rotation?.toInt() ?: 0
                if (rotation == "90" || rotation == "270") {
                    info.width = height.toInt()
                    info.height = width.toInt()
                } else {
                    info.width = width.toInt()
                    info.height = height.toInt()
                }
                return info
            }

        }

    }
}
