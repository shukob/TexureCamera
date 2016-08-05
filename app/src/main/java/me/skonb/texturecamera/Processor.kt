package me.skonb.texturecamera

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.github.hiteshsondhi88.libffmpeg.FFmpeg
import com.github.hiteshsondhi88.libffmpeg.FFmpegExecuteResponseHandler
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException
import java.io.File
import java.util.*
import java.util.regex.Pattern

/**
 * Created by skonb on 2016/08/02.
 */

class Processor(context: Context) {
    private var mCommand: ArrayList<String>? = null
    private var mFilters: ArrayList<String>? = null
    private var mMetaData: HashMap<String, String>? = null
    private val mNumCores: Int
    internal var context: Context? = null
    private val mFFmpeg: FFmpeg


    init {
        this.mFFmpeg = FFmpeg.getInstance(context)
        this.mNumCores = numCores
        this.context = context
    }

    private val numCores: Int
        get() {
            try {
                val i = File("/sys/devices/system/cpu/").listFiles { paramAnonymousFile -> Pattern.matches("cpu[0-9]", paramAnonymousFile.name) }.size
                return i
            } catch (localException: Exception) {
            }

            return 1
        }

    fun addInputPath(paramString: String): Processor {
        this.mCommand!!.add("-i")
        this.mCommand!!.add(paramString)
        return this
    }

    fun setAspectRatio(ratio: String): Processor {
        mCommand!!.add("-aspect")
        mCommand!!.add(ratio)
        return this
    }

    fun addCommand(command: String): Processor {
        mCommand!!.add(command)
        return this
    }

    fun addMetaData(paramString1: String, paramString2: String): Processor {
        this.mMetaData!!.put(paramString1, paramString2)
        return this
    }

    fun enableOverwrite(): Processor {
        this.mCommand!!.add("-y")
        return this
    }

    fun enableShortest(): Processor {
        this.mCommand!!.add("-shortest")
        return this
    }

    fun filterCrop(paramInt1: Int, paramInt2: Int): Processor {
        this.mFilters!!.add("crop=$paramInt1:$paramInt2")
        return this
    }

    fun reverseFilter(): Processor {
        mFilters!!.add("reverse")
        return this
    }

    fun newCommand(): Processor {
        this.mMetaData = HashMap()
        this.mFilters = ArrayList()
        this.mCommand = ArrayList()

        return this
    }

    fun process(paramArrayOfString: Array<String>, handler: FFmpegExecuteResponseHandler): Int {
        try {
            Log.i("TEST", TextUtils.join(" ", paramArrayOfString))
            mFFmpeg.execute(paramArrayOfString, handler)
        } catch (e: FFmpegCommandAlreadyRunningException) {
            e.printStackTrace()
            return 1
        }

        return 0
    }

    fun processToOutput(paramString: String, handler: FFmpegExecuteResponseHandler): Int {
        if (this.mFilters!!.size > 0) {
            this.mCommand!!.add("-vf")
            val localStringBuilder = StringBuilder()
            val localIterator3 = this.mFilters!!.iterator()
            while (localIterator3.hasNext()) {
                localStringBuilder.append(localIterator3.next())
                localStringBuilder.append(",")
            }
            val str2 = localStringBuilder.toString()
            this.mCommand!!.add(str2.substring(0, -1 + str2.length))
        }
        val localIterator1 = this.mMetaData!!.keys.iterator()
        while (localIterator1.hasNext()) {
            this.mCommand!!.add("-metadata")
            val key = localIterator1.next()
            this.mCommand!!.add(key + "=" + "\"" + this.mMetaData!![key] as String + "\"")
        }
        if (this.mNumCores > 1)
            this.mCommand!!.add(paramString)
        val localIterator2 = this.mCommand!!.iterator()
        if (localIterator2.hasNext())
            Log.i("Add arg '{}'", localIterator2.next())
        return process(this.mCommand!!.toTypedArray(), handler)
    }

    fun setAudioCopy(): Processor {
        this.mCommand!!.add("-acodec")
        this.mCommand!!.add("copy")
        return this
    }

    fun setBsfA(paramString: String): Processor {
        this.mCommand!!.add("-bsf:a")
        this.mCommand!!.add(paramString)
        return this
    }

    fun setBsfV(paramString: String): Processor {
        this.mCommand!!.add("-bsf:v")
        this.mCommand!!.add(paramString)
        return this
    }

    fun setCopy(): Processor {
        this.mCommand!!.add("-c")
        this.mCommand!!.add("copy")
        return this
    }

    fun setFormat(paramString: String): Processor {
        this.mCommand!!.add("-f")
        this.mCommand!!.add(paramString)
        return this
    }

    fun setFrames(paramLong: Long, paramInt: Int): Processor {
        this.mCommand!!.add("-vframes")
        this.mCommand!!.add((paramLong / 1000.0 * paramInt).toInt().toString())
        return this
    }

    fun setMap(paramString: String): Processor {
        this.mCommand!!.add("-map")
        this.mCommand!!.add(paramString + "?")
        return this
    }

    fun setMetaData(paramHashMap: HashMap<String, String>): Processor {
        this.mMetaData = paramHashMap
        return this
    }

    fun setShortest(): Processor {
        this.mCommand!!.add("-shortest")
        return this
    }

    fun setStart(paramLong: Long): Processor {
        this.mCommand!!.add("-ss")
        this.mCommand!!.add((paramLong / 1000.0).toString())
        return this
    }

    fun setTotalDuration(paramLong: Long): Processor {
        this.mCommand!!.add("-t")
        this.mCommand!!.add((paramLong / 1000.0).toString())
        return this
    }

    fun setVideoCopy(): Processor {
        this.mCommand!!.add("-vcodec")
        this.mCommand!!.add("copy")
        return this
    }

    fun useX264(): Processor {
        this.mCommand!!.add("-vcodec")
        this.mCommand!!.add("libx264")
        return this
    }

    fun setAudioConcatFilter(audioPaths: List<String>): Processor {
        for (path in audioPaths) {
            addInputPath(path)
        }

        mCommand!!.add("-filter_complex")
        mCommand!!.add(String.format("concat=n=%d:v=0:a=1", audioPaths.size))
        mCommand!!.add("-strict")
        mCommand!!.add("-2")
        return this
    }

    fun setTranscodeToMpegTransportStream(videoPath: String): Processor {
        addInputPath(videoPath)
        setCopy()
        setBsfV("h264_mp4toannexb")
        setFormat("mpegts")
        return this
    }


    fun setConcatFilter(videoPaths: List<String>, transpose: List<String>): Processor {

//        mCommand!!.add("-filter_complex")
        val builder = StringBuilder("concat:")
        for (path in videoPaths) {
            builder.append(path)
            if (videoPaths.indexOf(path) != videoPaths.size - 1) {
                builder.append("|")
            }
        }
        addInputPath(builder.toString())
//        builder.append(String.format("concat=n=%d:v=1:a=1 [v] [a]", videoPaths.size))
//        mCommand!!.add(builder.toString())
//        setMap("[v]")
//        setMap("[a]")
//        mCommand!!.add("-vn")
//        mCommand!!.add("-strict")
//        mCommand!!.add("-2")
        setCopy()
        return this
    }

    fun specifyFps(fps: Int): Processor {
        mFilters!!.add(String.format("fps=%d/1", fps))
        return this
    }

    fun combineFrames(fps: Int): Processor {
        mCommand!!.add("-framerate")
        mCommand!!.add(String.format("%d/1", fps))
        return this
    }

    fun libx264(): Processor {
        mCommand!!.add("-c:v")
        mCommand!!.add("libx264")
        return this
    }

    fun pixcelFormat(format: String): Processor {
        mCommand!!.add("-pix_fmt")
        mCommand!!.add(format)
        return this
    }

    fun extractAudio(channels: Int): Processor {
        mCommand!!.add("-ab")
        mCommand!!.add("160k")
        mCommand!!.add("-ac")
        mCommand!!.add(String.format("%d", channels))
        mCommand!!.add("-ar")
        mCommand!!.add("44100")
        mCommand!!.add("-vn")
        return this
    }

    fun scale(width: Int, height: Int): Processor {
        mFilters!!.add(String.format("scale=%d:%d", width, height))
        return this
    }

    fun setSepiaFilter(): Processor {
        mCommand!!.add("-filter_complex")
        mCommand!!.add("[0:v]colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131[colorchannelmixed];[colorchannelmixed]mp=eq2=1.0:0:1.3:2.4:1.0:1.0:1.0:1.0[color_effect]")
        setMap("[color_effect]")
        return this
    }

    fun addFilter(filter: String): Processor {
        mFilters!!.add(filter)
        return this
    }

}
