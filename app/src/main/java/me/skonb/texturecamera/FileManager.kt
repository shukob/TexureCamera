package me.skonb.texturecamera

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.*
import java.lang.ref.SoftReference
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by skonb on 2016/08/08.
 */
object FileManager {
    val VIDEO_EXTENSION = "mp4"
    val IMAGE_EXTENTION = "jpeg"
    val DIR_NAME = "Mizica"
    val IMAGE_DIR_NAME = "images"
    val VIDEO_DIR_NAME = "videos"
    val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")

    fun getImageDirectory(context: Context): File {
        val state = Environment.getExternalStorageState()
        var baseDir: File? = null
        if (state == Environment.MEDIA_MOUNTED) {
            baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), DIR_NAME)
        } else {
            baseDir = File(context.filesDir, IMAGE_DIR_NAME)
        }
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return baseDir
    }

    fun getVideoDirectory(context: Context): File {
        val state = Environment.getExternalStorageState()
        var baseDir: File? = null
        if (state == Environment.MEDIA_MOUNTED) {
            baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), DIR_NAME)
        } else {
            baseDir = File(context.filesDir, VIDEO_DIR_NAME)
        }
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return baseDir
    }

    fun getVideoFileFromTimeStamp(context: Context): File {
        val baseDir = getVideoDirectory(context)
        val res = File(baseDir, DATE_FORMAT.format(Date()) + "." + VIDEO_EXTENSION)
        if (res.exists()) {
            res.delete()
        }
        return res
    }

    fun getImageFileFromTimeStamp(context: Context): File {
        val baseDir = getImageDirectory(context)
        val res = File(baseDir, DATE_FORMAT.format(Date()) + "." + IMAGE_EXTENTION)
        if (res.exists()) {
            res.delete()
        }
        return res
    }

    fun isImageFileOfNameSavedInImageDirectory(fileName: String, context: Context): Boolean {
        val destination = File(getImageDirectory(context), fileName)
        return destination.exists()
    }

    fun saveBitmapToImageDirectory(bitmap: Bitmap, fileName: String,
                                   activity: Activity?): File? {
        try {
            val destination = File(getImageDirectory(activity!!), fileName)
            if (destination.exists()) {
                destination.delete()
            }
            destination.createNewFile()
            val fos = FileOutputStream(destination)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            fos.close()
            if (activity != null) {
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                val contentUri = Uri.fromFile(destination)
                mediaScanIntent.data = contentUri
                activity.sendBroadcast(mediaScanIntent)
                val values = ContentValues()
                values.put(MediaStore.Images.Media.DATA, destination.absolutePath)
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values)
            }
            return destination
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }

    }

    fun copyInputStreamToFile(context: Context, `is`: InputStream, outFile: File): Boolean {
        val buf = ByteArray(4096)
        var len: Int = 0

        try {
            val fos = FileOutputStream(outFile)
            while ({ len = `is`.read(buf);len }() > 0)
                fos.write(buf, 0, len)
            fos.close()
            return true
        } catch (e: FileNotFoundException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }

        return false

    }

    fun generateVideoThumbnail(videoFilePath: String): SoftReference<Bitmap> {
        val bitmapReference = SoftReference(ThumbnailUtils.createVideoThumbnail(videoFilePath, MediaStore.Video.Thumbnails.MINI_KIND))
        val stream = ByteArrayOutputStream()
        if (bitmapReference.get() != null) {
            bitmapReference.get().compress(Bitmap.CompressFormat.JPEG, 100, stream)
            val byteArray = stream.toByteArray()
            return SoftReference(BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size))
        } else {
            return SoftReference<Bitmap>(null)
        }
    }

}

