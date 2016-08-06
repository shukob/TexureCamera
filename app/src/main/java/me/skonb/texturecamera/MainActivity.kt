package me.skonb.texturecamera

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import com.github.hiteshsondhi88.libffmpeg.FFmpeg
import com.github.hiteshsondhi88.libffmpeg.FFmpegExecuteResponseHandler
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileInputStream

/**
 * Created by skonb on 2016/07/28.
 */
class MainActivity : Activity() {

    enum class Speed(val value: Float) {
        _1x(1f), _2x(2f), _3x(3f), _6x(6f),
        _1_6x(1f / 6f), _1_3x(1f / 3f), _1_2x(1f / 2f)
    }

    val enabledSpeeds = mutableListOf(Speed._1x)
    val progressDialogHelper = ProgressDialogHelper()
    var selectedSpeed = Speed._1x
        set(value) {
            if (field != value) {
                field = value
                reloadCamera()
            }
        }

    val TAG = "MainActivity"
    var camera: Camera? = null
    var flashMode = Camera.Parameters.FLASH_MODE_OFF
        set(value) {
            if (field != value) {
                camera?.let { camera ->
                    val params = camera.parameters
                    params.supportedFlashModes?.let {
                        if (it.contains(flashMode)) {
                            params.flashMode = flashMode
                            camera.parameters = params
                            field = value
                        }
                    }
                }
            }
        }
    var ret = MediaMetadataRetriever()
    var cameraID: Int? = null
    var surfaceTexture: SurfaceTexture? = null
    var surfaceWidth: Int = 0
    var surfaceHeight: Int = 0
    var previewWidth: Int = 0
    var previewHeight: Int = 0
    var videoWidth: Int = 0
    var videoHeight: Int = 0
    var recordingStartedAt: Long = 0L
    var previewStarted = false
    var mediaRecorder: MediaRecorder? = null
    var recordedVideoLength = 0.0
    var recording = false
        set(value) {
            field = value
            if (value) {
                button?.text = "停止"
            } else {
                button?.text = "録画"
            }
        }

    var displayOrientation: Int? = null
    var videoPathsList: MutableList<String> = mutableListOf()
    var videoRotationList: MutableList<Int> = mutableListOf()
    var handler: Handler? = null
    var ffmpegLoaded = false

    fun configureEnabledSpeeds(params: Camera.Parameters) {
        var array = IntArray(2)
        enabledSpeeds.clear()
        enabledSpeeds.add(Speed._1x)
        Log.i(TAG, "fps-range... ${params.supportedPreviewFpsRange.map { m -> m.joinToString(":") }.joinToString(",")}")
        params.getPreviewFpsRange(array)
        if (canCaptureTimelapse()) {
            for (speed in arrayOf(Speed._2x, Speed._3x, Speed._6x)) {
                if (array[0].toDouble() / 1000.0 <= 30.0 / speed.value &&
                        array[1].toDouble() / 1000.0 >= 30.0 / speed.value) {
                    enabledSpeeds.add(speed)
                }
            }
        }
        if (canCaptureSlowMotion()) {
            for (speed in arrayOf(Speed._1_2x, Speed._1_3x, Speed._1_6x)) {
                if (array[0].toDouble() / 1000.0 <= 30.0 / speed.value &&
                        array[1].toDouble() / 1000.0 >= 30.0 / speed.value) {
                    enabledSpeeds.add(speed)
                }
            }
        }
        if (enabledSpeeds.size > 1) {
            speed_button?.visibility = View.VISIBLE
        } else {
            speed_button?.visibility = View.GONE
        }
    }

    fun initFFMpeg() {
        object : AsyncTask<Unit, Unit, Unit>() {
            override fun doInBackground(vararg params: Unit): Unit {
                val ffmpeg = FFmpeg.getInstance(this@MainActivity)
                try {
                    ffmpeg.loadBinary(object : LoadBinaryResponseHandler() {

                        override fun onStart() {
                        }

                        override fun onFailure() {
                        }

                        override fun onSuccess() {
                            ffmpegLoaded = true
                        }

                        override fun onFinish() {
                        }
                    })
                } catch (e: FFmpegNotSupportedException) {
                    e.printStackTrace()
                }
            }
        }.execute()
    }


    fun reloadCamera() {
        stopRecording()
        releaseMediaRecorder()
        releaseCamera()
        openCamera(cameraID!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textureView?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture?): Boolean {
                stopPreview()
                this@MainActivity.surfaceTexture = null
                return true
            }

            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
                this@MainActivity.surfaceTexture = surfaceTexture
                surfaceWidth = width
                surfaceHeight = height
                camera?.let {
                    startPreview()
                }
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
                surfaceWidth = width
                surfaceHeight = height
                determineDisplayOrientation()
                configurePreviewTransform(textureView!!.width, textureView!!.height)
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture?) {
                this@MainActivity.surfaceTexture = surfaceTexture
            }
        }
        switch_camera_button?.setOnClickListener {
            if (cameraID == getBackCameraID()) {
                cameraID = getFrontCameraID()
            } else {
                cameraID = getBackCameraID()
            }
            reloadCamera()
        }
        button?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    seek_view?.addSection()
                }
                MotionEvent.ACTION_MOVE -> {
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (stopRecording()) {
                        val f = File(videoPathsList.last())
                        if (f.length() == 0L) {
                            videoPathsList.removeAt(videoPathsList.size - 1)
                        }
                    }
                }
            }
            true
        }

        done_button?.setOnClickListener {
            seek_view?.let {
                if (it.totalLength > 0) {
                    concatenateVideos()
                }
            }
        }
        cameraID = getBackCameraID()
        seek_view?.maximumLength = 10.0
        initFFMpeg()

        speed_button?.setOnClickListener {
            AlertDialog.Builder(this).setItems(enabledSpeeds.map { m -> "x${m.value}" }.toTypedArray(), { dialog, which ->
                selectedSpeed = enabledSpeeds[which]

            }).setTitle("速度を選択").setNegativeButton("キャンセル", null).show()
        }
    }

    fun openCamera(id: Int) {
        if (videoWidth == 0 && videoHeight == 0) {
            determineVideoSize()?.let { size ->
                videoWidth = size.width
                videoHeight = size.height
                Log.i(TAG, "video size: ($videoWidth, $videoHeight)")
            }
        }
        camera = getCamera(id)
        camera?.let {
            var parameters = it.parameters

            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            getOptimalSize(parameters.supportedPreviewSizes, 1280, 720)?.let { previewSize ->
                parameters.setPreviewSize(previewSize.width, previewSize.height);
                previewWidth = previewSize.width
                previewHeight = previewSize.height
                Log.i(TAG, "preview size: ($previewWidth, $previewHeight)")
            }

            if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            }

            parameters.supportedFlashModes?.let {
                if (it.contains(flashMode)) {
                    parameters.flashMode = flashMode
                }
            }

            configureEnabledSpeeds(parameters)
            it.parameters = parameters
            determineDisplayOrientation()
            surfaceTexture?.let {
                startPreview()
            }
        }
    }

    private fun determineDisplayOrientation() {
        cameraID?.let { cameraID ->
            val cameraInfo = Camera.CameraInfo()
            Camera.getCameraInfo(cameraID, cameraInfo)

            // Clockwise rotation needed to align the window display to the natural position
            val rotation = windowManager.defaultDisplay.rotation
            var degrees = 0
            when (rotation) {
                Surface.ROTATION_0 -> {
                    degrees = 0
                }
                Surface.ROTATION_90 -> {
                    degrees = 90
                }
                Surface.ROTATION_180 -> {
                    degrees = 180
                }
                Surface.ROTATION_270 -> {
                    degrees = 270
                }
            }

            var displayOrientation: Int

            // CameraInfo.Orientation is the angle relative to the natural position of the device
            // in clockwise rotation (angle that is rotated clockwise from the natural position)
            if (cameraInfo.facing === Camera.CameraInfo.CAMERA_FACING_FRONT) {
                // Orientation is angle of rotation when facing the camera for
                // the camera image to match the natural orientation of the device
                displayOrientation = (cameraInfo.orientation + degrees) % 360
                displayOrientation = (360 - displayOrientation) % 360
            } else {
                displayOrientation = (cameraInfo.orientation - degrees + 360) % 360
            }

            camera?.setDisplayOrientation(displayOrientation)
            this.displayOrientation = displayOrientation
        }
    }


    private fun getCamera(cameraID: Int): Camera? {
        try {
            return Camera.open(cameraID)
        } catch (e: Exception) {
            Log.d(TAG, "Can't open camera with id " + cameraID)
            e.printStackTrace()
        }
        return null
    }

    private fun getOptimalSize(sizes: List<Camera.Size>?, w: Int, h: Int, maxW: Int = Int.MAX_VALUE, maxH: Int = Int.MAX_VALUE): Camera.Size? {
        val ASPECT_TOLERANCE = 0.05
        val targetRatio = w.toDouble() / h

        if (sizes == null) return null

        var optimalSize: Camera.Size? = null

        var minDiff = java.lang.Double.MAX_VALUE

        val targetHeight = h

        // Find size
        for (size in sizes) {
            val ratio = size.width.toDouble() / size.height.toDouble()
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue
            if (Math.abs(size.height - targetHeight) < minDiff && size.height <= maxH && size.width <= maxW) {
                optimalSize = size
                minDiff = Math.abs(size.height - targetHeight).toDouble()
            }
        }

        if (optimalSize == null) {
            minDiff = java.lang.Double.MAX_VALUE
            for (size in sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff && size.height <= maxH && size.width <= maxW) {
                    optimalSize = size
                    minDiff = Math.abs(size.height - targetHeight).toDouble()
                }
            }
        }

        return optimalSize
    }

    private fun getFrontCameraID(): Int {
        val pm = packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            return Camera.CameraInfo.CAMERA_FACING_FRONT
        }

        return getBackCameraID()
    }

    private fun getBackCameraID(): Int {
        return Camera.CameraInfo.CAMERA_FACING_BACK
    }


    fun startPreview() {
        if (!previewStarted) {
            camera?.let { camera ->
                surfaceTexture?.let { surfaceTexture ->
                    camera.setPreviewTexture(surfaceTexture)
                    camera.startPreview()
                    previewStarted = true
                    configurePreviewTransform(surfaceWidth, surfaceHeight)
                    prepareRecording()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cameraID?.let {
            openCamera(it)
        }
        textureView?.keepScreenOn = true
    }

    override fun onPause() {
        releaseMediaRecorder()
        releaseCamera()
        textureView?.keepScreenOn = false

        super.onPause()
    }

    fun stopPreview() {
        if (previewStarted) {
            camera?.let { camera ->
                camera.stopPreview()
            }
        }
        previewStarted = false
    }


    fun releaseCamera() {
        stopPreview()
        camera?.let {
            it.release()
        }
        camera = null
    }

    private fun configurePreviewTransform(viewWidth: Int, viewHeight: Int) {
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        var bufferRect = RectF(0f, 0f, previewHeight.toFloat(), previewWidth.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        val viewAR = viewRect.width() / viewRect.height()
        val surfaceAR = bufferRect.width() / bufferRect.height()
        if (viewAR > surfaceAR) {
            matrix.postScale(1f, viewRect.height() / bufferRect.height(), centerX, centerY)
        } else {
            matrix.postScale(viewRect.width() / bufferRect.width(), 1f, centerX, centerY)
        }
        if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        } else if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        }
        textureView?.setTransform(matrix)
    }

    fun configureGhostTransform(viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int) {
        val matrix = Matrix()
        val scale = Math.max(
                viewHeight.toFloat() / imageHeight.toFloat(),
                viewWidth.toFloat() / imageWidth.toFloat())
        matrix.postScale(scale, scale, 0f, 0f)
        matrix.postTranslate((viewWidth - imageWidth * scale) / 2f, (viewHeight - imageHeight * scale) / 2f)
        ghost_view?.imageMatrix = matrix
    }


    fun getTemporaryFile(): File {
        return File(externalCacheDir, "test_${System.currentTimeMillis()}.mp4")
    }

    var output: String? = null
    fun prepareRecording() {
        mediaRecorder = MediaRecorder()
        camera?.unlock()
        mediaRecorder?.setCamera(camera)
        if (cameraID == getBackCameraID()) {
            mediaRecorder?.setOrientationHint(displayOrientation!!)
        } else {
            mediaRecorder?.setOrientationHint((displayOrientation!! + 180) % 360)
        }

        mediaRecorder?.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        if (selectedSpeed == Speed._1x) {
            mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        }
        val profile = CamcorderProfile.get(getOptimalProfile())
        if (selectedSpeed != Speed._1x) {
            mediaRecorder?.setCaptureRate(profile.videoFrameRate / selectedSpeed.value.toDouble())
        }
        mediaRecorder?.setProfile(profile)
        mediaRecorder?.setVideoSize(videoWidth, videoHeight)
        output = getTemporaryFile().absolutePath
        mediaRecorder?.setOutputFile(output)
        mediaRecorder?.prepare()
    }


    fun getOptimalProfile(): Int {
        when (selectedSpeed) {
            Speed._1x -> {
                for (profile in arrayOf(CamcorderProfile.QUALITY_720P, CamcorderProfile.QUALITY_480P)) {
                    if (CamcorderProfile.hasProfile(cameraID!!, profile)) {
                        return profile
                    }
                }
                return CamcorderProfile.QUALITY_LOW
            }
            Speed._6x, Speed._2x, Speed._3x -> {
                for (profile in arrayOf(CamcorderProfile.QUALITY_TIME_LAPSE_720P, CamcorderProfile.QUALITY_TIME_LAPSE_480P)) {
                    if (CamcorderProfile.hasProfile(cameraID!!, profile)) {
                        return profile
                    }
                }
                return CamcorderProfile.QUALITY_TIME_LAPSE_LOW
            }
            Speed._1_6x, Speed._1_3x, Speed._1_2x -> {
                for (profile in arrayOf(CamcorderProfile.QUALITY_HIGH_SPEED_720P, CamcorderProfile.QUALITY_HIGH_SPEED_480P)) {
                    if (CamcorderProfile.hasProfile(cameraID!!, profile)) {
                        return profile
                    }
                }
                return CamcorderProfile.QUALITY_HIGH_SPEED_LOW
            }


        }

    }


    fun startRecording() {
        if (!recording) {
            mediaRecorder?.let {
                seek_view?.addSection()
                it.start()
                recording = true
                recordingStartedAt = System.currentTimeMillis()
                startSeekbarProgress()
            }
        }
    }

    fun stopRecording(): Boolean {
        if (recording) {
            try {
                mediaRecorder?.stop()

                videoPathsList.add(output!!)
                if (cameraID == getBackCameraID()) {
                    videoRotationList.add(displayOrientation!!)
                } else {
                    videoRotationList.add((displayOrientation!! + 180) % 360)
                }
                recordedVideoLength = seek_view?.totalLength ?: 0.0
                updateGhost()
            } catch(e: RuntimeException) {
                e.printStackTrace()
                seek_view?.totalLength = recordedVideoLength
                return false
            } finally {
                releaseMediaRecorder()
                prepareRecording()
                endSeekbarProgress()
                recording = false
            }
            return true
        } else {
            return false
        }
    }

    fun releaseMediaRecorder() {
        mediaRecorder?.let {
            it.reset()
            it.release()
            camera?.lock()
            output?.let { output ->
                val f = File(output)
                if (f.length() == 0L) {
                    f.delete()
                }
            }
        }
        mediaRecorder = null
    }

    fun startSeekbarProgress() {
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message?) {
                super.handleMessage(msg)
                val time = System.currentTimeMillis()
                seek_view?.totalLength = recordedVideoLength + ((time - recordingStartedAt) / 1000.0) / selectedSpeed.value
                if (recording) {
                    sendEmptyMessageDelayed(0, 33)
                }
            }
        }
        handler?.sendEmptyMessageDelayed(0, 33)
    }

    fun endSeekbarProgress() {
        handler?.removeMessages(0)
        handler = null
    }


    fun concatenateVideos() {
        if (ffmpegLoaded) {
            val time = System.currentTimeMillis()
            progressDialogHelper.showProgressDialog(this)
            val outputPath = File(externalCacheDir, "test_${System.currentTimeMillis()}.mp4").absolutePath
            VideoUtil().concatenateMultipleVideos(this@MainActivity, videoPathsList, videoRotationList, outputPath, object : FFmpegExecuteResponseHandler {
                override fun onFinish() {
                    progressDialogHelper.hideProgressDialog(this@MainActivity)
                    AlertDialog.Builder(this@MainActivity)
                            .setTitle("結合終了")
                            .setMessage("所要時間: ${(System.currentTimeMillis() - time).toFloat() / 1000f}秒")
                            .setNegativeButton("OK", null)
                            .show()

                }

                override fun onStart() {
                }

                override fun onSuccess(message: String?) {
                    Log.i(TAG, message)
                }

                override fun onFailure(message: String?) {

                    Log.i(TAG, message)
                }

                override fun onProgress(message: String?) {
                    Log.i(TAG, message)
                }
            })
        } else {
            AlertDialog.Builder(this).setMessage("FFMpegがロードされていません。").setPositiveButton("OK", null).show()
        }
    }

    fun determineVideoSize(): Camera.Size? {
        if (getFrontCameraID() != getBackCameraID()) {
            var videoSizes: MutableList<List<Camera.Size>> = mutableListOf()
            for (id in arrayOf(getFrontCameraID(), getBackCameraID())) {
                getCamera(id)?.let { camera ->
                    val params = camera.parameters
                    videoSizes.add(params.supportedVideoSizes)
                    camera.release()
                }
            }
            var list: MutableList<Camera.Size> = mutableListOf()
            videoSizes[0].forEach { size ->
                videoSizes[1].find { s ->
                    s.width == size.width && s.height == size.height
                }?.let {
                    list.add(it)
                }
            }
            return getOptimalSize(list, 720, 1280, 1280, 720)
        } else {
            val camera = getCamera(getBackCameraID())
            val size = camera?.parameters?.let { parameters ->
                getOptimalSize(parameters.supportedVideoSizes, 720, 1280, 1280, 720)
            }
            camera?.release()
            return size
        }
    }

    fun canCaptureTimelapse(): Boolean {
        return CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_TIME_LAPSE_LOW)
    }

    fun canCaptureSlowMotion(): Boolean {
        return CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_HIGH_SPEED_LOW)
    }

    fun updateGhost() {
        object : AsyncTask<String, Void, Bitmap?>() {
            override fun doInBackground(vararg path: String): Bitmap? {
                val ret = MediaMetadataRetriever()
                val stream = FileInputStream(File(path[0]))
                ret.setDataSource(stream.fd)
                val duration = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
                val res = ret.getFrameAtTime(duration * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ret.release()
                stream.close()
                return res
            }

            override fun onPostExecute(result: Bitmap?) {
                super.onPostExecute(result)
                result?.let { result ->
                    ghost_view?.let {
                        it.setImageBitmap(result)
                        configureGhostTransform(it.width, it.height, result.width, result.height)
                    }
                }
            }
        }.execute(videoPathsList.last())
    }

    internal fun reverseVideo(videoPath: String, callback: () -> Unit) {
        val dir = File(externalCacheDir, "${System.currentTimeMillis()}_image_extracted")
        dir.mkdirs()
        val path = File(dir, "/normal_order_frame%03d.jpg").absolutePath
        progressDialogHelper.showProgressDialog(this)
        VideoUtil().extractFrames(this, videoPath, path, object : FFmpegExecuteResponseHandler {
            override fun onSuccess(message: String) {
                Log.i(TAG, message)
                val frameFiles = dir.listFiles { dir, filename -> filename.startsWith("normal_order_frame") }
                var i = 1
                for (f in frameFiles) {
                    f.renameTo(File(dir, String.format("/reverse_order_frame%03d.jpg", i)))
                    ++i
                }
                val reversedPath = getTemporaryFile().absolutePath
                VideoUtil().combineImagesToVideo(this@MainActivity, File(dir, "/reverse_order_frame%03d.jpg").absolutePath, reversedPath, object : FFmpegExecuteResponseHandler {
                    override fun onSuccess(message: String) {
                        Log.i(TAG, message)
                    }

                    override fun onProgress(message: String) {
                        Log.i(TAG, message)

                    }

                    override fun onFailure(message: String) {
                        Log.i(TAG, message)
                    }

                    override fun onStart() {

                    }

                    override fun onFinish() {
                        for (f in dir.listFiles()) {
                            if (f.exists()) {
                                f.delete()
                            }
                        }
                        dir.delete()
                        var f = File(videoPath)
                        if (f.exists()) {
                            f.delete()
                        }
                        f = File(reversedPath)
                        if (f.exists()) {
                            f.renameTo(File(videoPath))
                        }
                        progressDialogHelper.hideProgressDialog(this@MainActivity)
                        callback()
                    }

                })
            }

            override fun onProgress(message: String) {
                Log.i(TAG, message)
            }

            override fun onFailure(message: String) {
                Log.i(TAG, message)
            }

            override fun onStart() {

            }

            override fun onFinish() {

            }
        })
    }
}
