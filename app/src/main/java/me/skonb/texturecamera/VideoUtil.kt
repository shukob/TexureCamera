package me.skonb.texturecamera

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.hardware.Camera
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.widget.Toast
import com.github.hiteshsondhi88.libffmpeg.FFmpegExecuteResponseHandler
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by skonb on 2016/08/02.
 */

class VideoUtil {
    var videoContentValues: ContentValues? = null


    fun determineDisplayOrientation(activity: Activity, defaultCameraId: Int): Int {
        var displayOrientation = 0
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
            val cameraInfo = Camera.CameraInfo()
            Camera.getCameraInfo(defaultCameraId, cameraInfo)

            val degrees = getRotationAngle(activity)


            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                displayOrientation = (cameraInfo.orientation + degrees) % 360
                displayOrientation = (360 - displayOrientation) % 360
            } else {
                displayOrientation = (cameraInfo.orientation - degrees + 360) % 360
            }
        }
        return displayOrientation
    }

    fun getRotationAngle(activity: Activity): Int {
        val rotation = activity.windowManager.defaultDisplay.rotation
        var degrees = 0

        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0

            Surface.ROTATION_90 -> degrees = 90

            Surface.ROTATION_180 -> degrees = 180

            Surface.ROTATION_270 -> degrees = 270
        }
        return degrees
    }

    fun getRotationAngle(rotation: Int): Int {
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0

            Surface.ROTATION_90 -> degrees = 90

            Surface.ROTATION_180 -> degrees = 180

            Surface.ROTATION_270 -> degrees = 270
        }
        return degrees
    }

    fun createFinalPath(): String {
        val dateTaken = System.currentTimeMillis()
        val title = CONSTANTS.FILE_START_NAME + dateTaken
        val filename = title + CONSTANTS.VIDEO_EXTENSION
        val filePath = genrateFilePath(dateTaken.toString(), true, null)
        val values = ContentValues(7)
        values.put(MediaStore.Video.Media.TITLE, title)
        values.put(MediaStore.Video.Media.DISPLAY_NAME, filename)
        values.put(MediaStore.Video.Media.DATE_TAKEN, dateTaken)
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/3gpp")
        values.put(MediaStore.Video.Media.DATA, filePath)
        videoContentValues = values

        return filePath
    }

    private fun genrateFilePath(uniqueId: String, isFinalPath: Boolean, tempFolderPath: File?): String {
        val fileName = CONSTANTS.FILE_START_NAME + uniqueId + CONSTANTS.VIDEO_EXTENSION
        var dirPath = ""
        if (isFinalPath) {
            File(CONSTANTS.CAMERA_FOLDER_PATH).mkdirs()
            dirPath = CONSTANTS.CAMERA_FOLDER_PATH
        } else
            dirPath = tempFolderPath!!.absolutePath
        val filePath = dirPath + "/" + fileName
        return filePath
    }

    fun createTempPath(tempFolderPath: File): String {
        val dateTaken = System.currentTimeMillis()
        val filePath = genrateFilePath(dateTaken.toString(), false, tempFolderPath)
        return filePath
    }


    val tempFolderPath: File
        get() {
            val tempFolder = File(CONSTANTS.TEMP_FOLDER_PATH + "_" + System.currentTimeMillis())
            return tempFolder
        }


    fun getResolutionList(camera: Camera): List<Camera.Size> {
        val parameters = camera.parameters
        val previewSizes = parameters.supportedPreviewSizes


        return previewSizes
    }

    fun calculateMargin(previewWidth: Int, screenWidth: Int): Int {
        var margin = 0
        if (previewWidth <= CONSTANTS.RESOLUTION_LOW) {
            margin = (screenWidth * 0.12).toInt()
        } else if (previewWidth > CONSTANTS.RESOLUTION_LOW && previewWidth <= CONSTANTS.RESOLUTION_MEDIUM) {
            margin = (screenWidth * 0.08).toInt()
        } else if (previewWidth > CONSTANTS.RESOLUTION_MEDIUM && previewWidth <= CONSTANTS.RESOLUTION_HIGH) {
            margin = (screenWidth * 0.08).toInt()
        }
        return margin


    }

    fun setSelectedResolution(previewHeight: Int): Int {
        var selectedResolution = 0
        if (previewHeight <= CONSTANTS.RESOLUTION_LOW) {
            selectedResolution = 0
        } else if (previewHeight > CONSTANTS.RESOLUTION_LOW && previewHeight <= CONSTANTS.RESOLUTION_MEDIUM) {
            selectedResolution = 1
        } else if (previewHeight > CONSTANTS.RESOLUTION_MEDIUM && previewHeight <= CONSTANTS.RESOLUTION_HIGH) {
            selectedResolution = 2
        }
        return selectedResolution


    }

    class ResolutionComparator : Comparator<Camera.Size> {
        override fun compare(size1: Camera.Size, size2: Camera.Size): Int {
            if (size1.height != size2.height)
                return size1.height - size2.height
            else
                return size1.width - size2.width
        }
    }

    fun concatenateMultipleFiles(inpath: String, outpath: String) {
        val Folder = File(inpath)
        val files: Array<File>
        files = Folder.listFiles()

        if (files.size > 0) {
            for (i in files.indices) {
                var `in`: Reader? = null
                var out: Writer? = null
                try {
                    `in` = FileReader(files[i])
                    out = FileWriter(outpath, true)
                    `in`.close()
                    out.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
        }
    }

    fun transpose(paramContext: Context, videoPath: String, output: String, handler: FFmpegExecuteResponseHandler) {
        val processor = Processor(paramContext).newCommand()
        processor.addInputPath(videoPath).addFilter("transpose=2,transpose=2").addCommand("-c:a").addCommand("copy").addCommand("-metadata:s:v:0").addCommand("rotate=90").enableOverwrite()
                .processToOutput(output, handler)
    }

    fun transcodeToMpegTransportStream(paramContext: Context, videoPath: String, callback: ((outPath: String?) -> Unit)?) {
        val processor = Processor(paramContext).newCommand()
        val output = File(paramContext.externalCacheDir, "temp_${System.currentTimeMillis()}.ts")
        processor.addInputPath(videoPath).addFilter("hflip,hflip").setAudioCopy().setBsfV("h264_mp4toannexb").setFormat("mpegts").enableOverwrite().processToOutput(output
                .absolutePath,
                object : FFmpegExecuteResponseHandler {
                    override fun onFinish() {
                    }

                    override fun onStart() {
                    }

                    override fun onSuccess(message: String?) {
                        Log.i("test", message)
                        callback?.invoke(output.absolutePath)
                    }

                    override fun onFailure(message: String?) {
                        Log.i("test", message)
                        callback?.invoke(null)
                    }

                    override fun onProgress(message: String?) {
                        Log.i("test", message)
                    }
                })
    }

    fun recursivelyTranscode(paramContext: Context, videoPaths: List<String>, index: Int, intermediates: MutableList<String>, callback: ((sucess: Boolean) -> Unit)?) {
        transcodeToMpegTransportStream(paramContext, videoPaths[index], { outPath ->
            outPath?.let { outPath ->
                intermediates.add(outPath)
                if (index + 1 < videoPaths.size) {
                    recursivelyTranscode(paramContext, videoPaths, index + 1, intermediates, callback)
                } else {
                    callback?.invoke(true)
                }
            } ?: callback?.invoke(false)
        })
    }


    fun concatenateMultipleVideos(paramContext: Context, videoPaths: List<String>, rotations: List<Int>, outPath: String, handler: FFmpegExecuteResponseHandler) {
        var intermediates = mutableListOf<String>()
        recursivelyTranscode(paramContext, videoPaths, 0, intermediates, { success ->
            if (success) {
                var processor = Processor(paramContext).newCommand()
                processor.setFormat("mpegts").addInputPath("concat:${intermediates.joinToString("|")}").setCopy().setBsfA("aac_adtstoasc").enableOverwrite().addCommand("-metadata:s:v:0").addCommand("rotate=0").processToOutput(outPath,
                        handler)
            } else {
                handler.onFailure("transcode fails")
            }
        })
    }

    fun concatenateMultipleAudios(paramContext: Context, audioPaths: List<String>, outPath: String, handler: FFmpegExecuteResponseHandler): Int {
        var processor = Processor(paramContext).newCommand()
        processor = processor.setAudioConcatFilter(audioPaths).enableOverwrite()
        return processor.processToOutput(outPath, handler)
    }


    fun combineVideoAndAudio(paramContext: Context, mCurrentVideoOutput: String, mAudioFilename: String, mOutput: String, handler: FFmpegExecuteResponseHandler): Int {
        return Processor(paramContext).newCommand().addInputPath(mCurrentVideoOutput).addInputPath(mAudioFilename).setMap("0:0").setMap("1:0").setCopy().setMetaData(metaData).enableOverwrite().setAspectRatio("16:9").processToOutput(mOutput, handler)

    }

    fun reverseVideo(paramContext: Context, input: String, output: String, handler: FFmpegExecuteResponseHandler): Int {
        return Processor(paramContext).newCommand().addInputPath(input).reverseFilter().processToOutput(output, handler)
    }

    fun extractFrames(context: Context, input: String, outputFormat: String, handler: FFmpegExecuteResponseHandler): Int {
        return Processor(context).newCommand().addInputPath(input).specifyFps(30).processToOutput(outputFormat, handler)
    }

    fun combineImagesToVideo(context: Context, inputFormat: String, output: String, handler: FFmpegExecuteResponseHandler): Int {
        return Processor(context).newCommand().combineFrames(30).addInputPath(inputFormat).libx264().specifyFps(30).pixcelFormat("yuv420p").processToOutput(output, handler)
    }


    private val metaData: HashMap<String, String>
        get() {
            val localHashMap = HashMap<String, String>()
            localHashMap.put("creation_time", SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSSZ").format(Date()))
            return localHashMap
        }

    fun getTimeStampInNsFromSampleCounted(paramInt: Int): Int {
        return (paramInt / 0.0441).toInt()
    }


    fun showToast(context: Context?, textMessage: String?, timeDuration: Int): Toast? {
        var textMessage = textMessage
        if (null == context) {
            return null
        }
        textMessage = if (null == textMessage) "Oops! " else textMessage.trim { it <= ' ' }
        val t = Toast.makeText(context, textMessage, timeDuration)
        t.show()
        return t
    }

    fun extractAudio(context: Context, input: String, output: String, handler: FFmpegExecuteResponseHandler): Int {
        return Processor(context).newCommand().addInputPath(input).extractAudio(1).processToOutput(output, handler)
    }

    fun scale(context: Context, input: String, output: String, width: Int, height: Int, handler: FFmpegExecuteResponseHandler): Int {
        return Processor(context).newCommand().addInputPath(input).scale(width, height).processToOutput(output, handler)
    }

    fun addSilentAudio(context: Context, input: String, output: String, handler: FFmpegExecuteResponseHandler): Int {
        return Processor(context).newCommand().addCommand("-ar").addCommand("44100").addCommand("-ac").addCommand("2").setFormat("s16le").addInputPath("/dev/zero").addInputPath(input).addCommand("-strict").addCommand("-2").addCommand("-shortest").setVideoCopy().addCommand("-c:a").addCommand("aac").processToOutput(output, handler)
    }

    class BitmapOverlay {

        class Builder {
            internal var overlay = BitmapOverlay()

            fun setBitmap(bitmap: Bitmap): Builder {
                overlay.bitmap = bitmap
                return this
            }

            fun setSize(width: Int, height: Int): Builder {
                overlay.width = width
                overlay.height = height
                return this
            }

            fun setPosition(x: Int, y: Int): Builder {
                overlay.x = x
                overlay.y = y
                return this
            }

            fun setRotation(rotation: Float): Builder {
                overlay.rotation = rotation
                return this
            }

            fun setTiming(from: Float, to: Float): Builder {
                overlay.fromTime = from
                overlay.toTime = to
                return this
            }

            fun build(): BitmapOverlay {
                return overlay
            }
        }

        var bitmap: Bitmap? = null
        var width: Int = 0
        var height: Int = 0
        var x: Int = 0
        var y: Int = 0
        var rotation: Float = 0.toFloat()
        var fromTime: Float = 0.toFloat()
        var toTime: Float = 0.toFloat()
    }

    class Music(var path: String, var fromTime: Float, var toTime: Float)

    fun decorate(context: Context, input: String, bitmapOverlays: List<BitmapOverlay>, music: Music?, output: String, handler: FFmpegExecuteResponseHandler): Int {
        val processor = Processor(context).newCommand()
        if (music != null) {
            processor.addCommand("-itsoffset").addCommand(music.fromTime.toString())
            processor.addInputPath(music.path)
        } else {
            processor.addInputPath(input)
        }
        processor.addInputPath(input)
        for (bitmapOverlay in bitmapOverlays) {

        }
        processor.setMap("0:a")
        processor.setMap("1:v")
        processor.setVideoCopy().setAudioCopy().addCommand("-shortest")
        return processor.processToOutput(output, handler)
    }

}
