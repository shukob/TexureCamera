package me.skonb.texturecamera

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.CamcorderProfile
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
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
import io.nlopez.smartlocation.SmartLocation
import io.nlopez.smartlocation.location.config.LocationParams
import io.nlopez.smartlocation.location.providers.LocationManagerProvider
import io.nlopez.smartlocation.rx.ObservableFactory
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Created by skonb on 2016/07/28.
 */
class MainActivity : Activity() {
    companion object {
        internal val REQUEST_PICK_MOVIE = 101
        internal val PERMISSION_REQUEST_LOCATION = 120
    }

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
    var transcodedVideoPathsList: MutableList<String> = mutableListOf()
    var handler: Handler? = null
    var converterHandler: Handler? = null
    var ffmpegLoaded = false
    var lastLocation: Location = Location("")
    var lastDate: Date? = null


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
        startFindingLocation()
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
                        } else {
                            convertTargetQueue.add(videoPathsList.last())
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

        pick_button?.setOnClickListener {
            pickMovie()
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
        startConverting()
    }

    override fun onPause() {
        releaseMediaRecorder()
        releaseCamera()
        textureView?.keepScreenOn = false
        stopConverting()
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
        val height = previewWidth
        val width = previewHeight
        val horizontalScale = viewWidth.toFloat() / width
        val verticalScale = viewHeight.toFloat() / height
        if (horizontalScale > verticalScale) {
            matrix.postScale(1f, horizontalScale / verticalScale, 0f, 0f)
            val scaledHeight = height * horizontalScale / verticalScale
//            matrix.postTranslate(0f, -(scaledHeight - viewHeight) / 2f)
        } else {
            matrix.postScale(verticalScale / horizontalScale, 1f, 0f, 0f)
            val scaledWidth = width * verticalScale / horizontalScale
//            matrix.postTranslate(-(scaledWidth - viewWidth) / 2f, 0f)
        }
//
        if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, viewWidth / 2f, viewHeight / 2f)
        } else if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            matrix.postRotate((90 * (rotation - 2)).toFloat(), viewWidth / 2f, viewHeight / 2f)
        }
        textureView?.setTransform(matrix)
    }

    fun configureGhostTransform(viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int) {
//        val height = previewWidth
//        val width = previewHeight
//        val horizontalScale = viewWidth.toFloat() / width
//        val verticalScale = viewHeight.toFloat() / height
//        val previewScale = Math.max(horizontalScale, verticalScale)
//        val previewAR = width.toFloat() / height.toFloat()
//        val imageAR = imageWidth.toFloat() / imageHeight.toFloat()
//        val matrix = Matrix()
//        if (cameraID == getFrontCameraID()) {
//            matrix.postScale(-1f, 1f)
//            matrix.postTranslate(imageWidth.toFloat(), 0f)
//        }
//        if (previewAR < imageAR) {
//            val scale = width.toFloat() / imageWidth.toFloat()
//            val translate = (imageHeight.toFloat() * scale - height) / 2f
//            matrix.postScale(scale, scale)
//            matrix.postTranslate(0f, translate)
//        } else {
//            val scale = height.toFloat() / imageHeight.toFloat()
//            val translate = (imageWidth.toFloat() * scale - width) / 2f
//            matrix.postScale(scale, scale)
//            matrix.postTranslate(translate, 0f)
//        }
//        matrix.postScale(previewScale, previewScale)
//        ghost_view?.imageMatrix = matrix
    }


    fun getTemporaryFile(extention: String = "mp4"): File {
        return File(externalCacheDir, "test_${System.currentTimeMillis()}.$extention")
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
            setMuteAll(true)
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
                setMuteAll(false)
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
            object : AsyncTask<Unit, Unit, Unit>() {
                override fun doInBackground(vararg params: Unit?): Unit {
                    while (!convertTargetQueue.isEmpty() || FFmpeg.getInstance(this@MainActivity).isFFmpegCommandRunning) {
                        Thread.sleep(100)
                    }
                    VideoUtil().concatenate(this@MainActivity, transcodedVideoPathsList, outputPath, object : FFmpegExecuteResponseHandler {
                        override fun onFinish() {
                            progressDialogHelper.hideProgressDialog(this@MainActivity)
                            AlertDialog.Builder(this@MainActivity)
                                    .setTitle("結合終了")
                                    .setMessage("所要時間: ${(System.currentTimeMillis() - time).toFloat() / 1000f}秒")
                                    .setNegativeButton("OK", null)
                                    .show()
                            cleanupRecording()
                            val time2 = System.currentTimeMillis()
                            decorate(outputPath, {
                                AlertDialog.Builder(this@MainActivity)
                                        .setTitle("BGM合成終了")
                                        .setMessage("所要時間: ${(System.currentTimeMillis() - time2).toFloat() / 1000f}秒")
                                        .setNegativeButton("OK", null)
                                        .show()


                            })
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
                    return
                }
            }.execute()
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
        ghost_view?.setImageBitmap(textureView.bitmap)
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

    internal fun pickMovie() {
        val moviePickerIntent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        val chooserIntent = Intent.createChooser(moviePickerIntent, "動画を選択するアプリを選んで下さい")
        startActivityForResult(chooserIntent, REQUEST_PICK_MOVIE)
    }

    val convertTargetQueue = LinkedBlockingQueue<String>()
    fun startConverting() {
        converterHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message?) {
                super.handleMessage(msg)
                if (!FFmpeg.getInstance(this@MainActivity).isFFmpegCommandRunning && !convertTargetQueue.isEmpty()) {
                    val target = convertTargetQueue.peek()
                    VideoUtil().transcodeToMpegTransportStream(this@MainActivity, target, { outPath ->
                        transcodedVideoPathsList.add(outPath!!)
                        convertTargetQueue.remove()
                        sendEmptyMessageDelayed(0, 33)
                    })
                } else {
                    sendEmptyMessageDelayed(0, 33)
                }
            }
        }
        converterHandler?.sendEmptyMessageDelayed(0, 33)
    }

    fun stopConverting() {
        converterHandler?.removeMessages(0)
        converterHandler = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_PICK_MOVIE -> {
                if (resultCode == Activity.RESULT_OK) {
                    var selectedVideo: Uri? = null
                    selectedVideo = data!!.data
                    val filePathColumn = arrayOf(MediaStore.Video.Media.DATA, MediaStore.Video.Media.DURATION)
                    val cursor = contentResolver.query(
                            selectedVideo, filePathColumn, null, null, null)
                    cursor.moveToFirst()

                    var columnIndex = cursor.getColumnIndex(filePathColumn[0])

                    val filePath = cursor.getString(columnIndex)
                    cursor.moveToFirst()

                    columnIndex = cursor.getColumnIndex(filePathColumn[1])
                    var duration = cursor.getLong(columnIndex)
                    cursor.close()
                    val path = getTemporaryFile().absolutePath
                    try {
                        FileManager.copyInputStreamToFile(this, if (filePath != null)
                            FileInputStream(filePath)
                        else
                            contentResolver.openInputStream(selectedVideo), File(path))
                        convertTo720p(path, { outPath ->
                            outPath?.let {
                                videoPathsList.add(outPath)
                                videoRotationList.add(90)
                                MovieInfoRetriever.retrieve(path)?.let { info ->
                                    info.lat?.let {
                                        lastLocation.latitude = info.lat
                                        lastLocation.longitude = info.lng
                                    }
                                    lastDate = info.takenDate
                                }
                                AlertDialog.Builder(this@MainActivity).setTitle("メタデータ")
                                seek_view?.addSection()
                                if (duration == 0L) {
                                    val retriever = MediaMetadataRetriever()
                                    retriever.setDataSource(this, if (filePath != null) Uri.fromFile(File(filePath)) else selectedVideo)
                                    val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    val timeInMillisec = java.lang.Long.parseLong(time)
                                    duration = timeInMillisec
                                }
                                seek_view?.totalLength = recordedVideoLength + duration
                                recordedVideoLength = seek_view?.totalLength ?: 0.0
                            } ?: {
                                AlertDialog.Builder(this@MainActivity).
                                        setTitle("エラー").setMessage("動画の変換に失敗しました").setNegativeButton("OK", null).show()

                            }()
                        })
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                }
            }
        }
    }

    fun convertTo720p(videoPath: String, callback: ((path: String?) -> Unit)?) {
        MovieInfoRetriever.retrieve(videoPath)?.let { info ->
            if (info.width == 720 && info.height == 1280) {
                callback?.invoke(videoPath)
            } else {
                val f = getTemporaryFile()
                progressDialogHelper.showProgressDialog(this)
                VideoUtil().convertTo720p(this, videoPath, f.absolutePath, object : FFmpegExecuteResponseHandler {
                    override fun onFinish() {
                        progressDialogHelper.hideProgressDialog(this@MainActivity)
                        File(videoPath).delete()
                    }

                    override fun onStart() {

                    }

                    override fun onSuccess(message: String?) {
                        Log.i(TAG, message)
                        callback?.invoke(f.absolutePath)
                    }

                    override fun onFailure(message: String?) {
                        Log.i(TAG, message)
                        callback?.invoke(null)
                    }

                    override fun onProgress(message: String?) {
                        Log.i(TAG, message)
                    }
                })
            }
        }
    }


    fun startFindingLocation() {
        var locationControl: SmartLocation.LocationControl? = null
        locationControl = SmartLocation.with(this).location(LocationManagerProvider())
        if (locationControl!!.state().isGpsAvailable) {
            locationControl.config(if (Build.HARDWARE.startsWith("vbox86")) LocationParams.NAVIGATION else LocationParams.LAZY)
        } else if (locationControl.state().isNetworkAvailable) {
            locationControl.config(LocationParams.LAZY)
        } else if (locationControl.state().isPassiveAvailable) {
            locationControl.config(LocationParams.LAZY)
        }
        val locationFetched = HashMap<String, Boolean>()

        if (locationControl.state().isAnyProviderAvailable && locationControl.state().locationServicesEnabled()) {
            val locationObservable = ObservableFactory.from(locationControl)
            val finalLocationControl = locationControl
            locationObservable.timeout(5000, TimeUnit.MILLISECONDS).subscribe({ location ->
                runOnUiThread {
                    finalLocationControl.stop()
                    locationFetched.put("fetched", true)
                    if (location != null) {
                        this@MainActivity.lastLocation.latitude = location.latitude
                        this@MainActivity.lastLocation.longitude = location.longitude
                    } else {
                        if (SmartLocation.with(this).location().lastLocation != null) {
                            SmartLocation.with(this).location().lastLocation?.let { location ->
                                this@MainActivity.lastLocation.latitude = location.latitude
                                this@MainActivity.lastLocation.longitude = location.longitude

                            }
                        } else {
                            try {
                                fetchLocationManually(true)
                            } catch (e: SecurityException) {
                                e.printStackTrace()
                            }

                        }
                    }
                }
            }, { throwable ->
                runOnUiThread {
                    try {
                        //In some devices GoogleApiClient#isConnected returns true,
                        //then subsequent call of removeLocationUpdates raises 'Google ApiClient is not connected yet'.
                        //So guard that bug here.
                        finalLocationControl.stop()
                    } catch (e: Exception) {

                    }

                    if (throwable is TimeoutException && locationFetched["fetched"] == null) {
                        if (SmartLocation.with(this).location().lastLocation != null) {
                            SmartLocation.with(this).location().lastLocation?.let { location ->
                                this@MainActivity.lastLocation.latitude = location.latitude
                                this@MainActivity.lastLocation.longitude = location.longitude
                            }
                        } else {
                            try {
                                fetchLocationManually(true)
                            } catch (e: SecurityException) {
                                e.printStackTrace()
                            }

                        }
                    }
                }
            }) {

            }

        } else {
            ErrorDialogHelper.showErrorDialogWithMessage(this, "現在地が取得できません。位置情報サービスが有効になっていないか、有効な位置情報プロバイダが存在しません。")
        }
    }

    @Throws(SecurityException::class)
    internal fun fetchLocationManually(repeat: Boolean) {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                try {
                    locationManager.removeUpdates(this)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
                this@MainActivity.lastLocation.latitude = location.latitude
                this@MainActivity.lastLocation.longitude = location.longitude

            }

            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
                Log.i("", "status: " + status)
            }

            override fun onProviderEnabled(provider: String) {
                Log.i("", "provider: " + provider)
            }

            override fun onProviderDisabled(provider: String) {
                try {
                    locationManager.removeUpdates(this)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }

                runOnUiThread {
                    ErrorDialogHelper.showErrorDialogWithMessage(this@MainActivity, "位置情報の取得に失敗しました")
                }
            }
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, listener)
        } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0f, listener)
        } else if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0, 0f, listener)
        }
        val myHandler = Handler(Looper.getMainLooper())
        myHandler.postDelayed({
            locationManager.removeUpdates(listener)
            if (repeat) {
                fetchLocationManually(false)
            } else {
                runOnUiThread {
                    val loc = Location("")
//                    loc.latitude = SysConfigData.getValueByKey(Define.SYS_CONFIG_KEY_DEFAULT_LATITUDE).toDouble()
//                    loc.longitude = SysConfigData.getValueByKey(Define.SYS_CONFIG_KEY_DEFAULT_LONGITUDE).toDouble()
                    this.lastLocation = loc
                }
            }
        }, 5000)
    }

    fun decorate(videoPath: String?, callback: ((outPath: String?) -> Unit)?) {
        val info = MovieInfoRetriever.retrieve(videoPath)
        val bgmPath = getTemporaryFile("mp3")
        val bitmapPath = getTemporaryFile("png")
        FileManager.copyInputStreamToFile(this, contentResolver.openInputStream(Uri.parse("android.resource://${packageName}/${R.raw.bgm}")!!), bgmPath)
        FileManager.copyInputStreamToFile(this, contentResolver.openInputStream(Uri.parse("android.resource://${packageName}/${R.drawable.ic_media_play}")!!), bitmapPath)
        val outFile = getTemporaryFile()
        val overlays = mutableListOf<VideoUtil.BitmapOverlay>()
        overlays.add(VideoUtil.BitmapOverlay.Builder().
                setTiming(1f, 3f).
                setBitmapPath(bitmapPath.absolutePath).setSize(100, 100).setRotation(Math.PI.toFloat() / 3f).setMovieDuration((info!!.duration / 1000f).toFloat())
                .build())
        VideoUtil().decorate(this, videoPath!!, (info!!.duration / 1000f), lastLocation, overlays, VideoUtil.Music(bgmPath.absolutePath, 10f, 10f + (info!!.duration / 1000f)),
                VideoUtil.Filter.None, outFile.absolutePath,
                object :
                        FFmpegExecuteResponseHandler {
                    override fun onFinish() {
                        callback?.invoke(outFile.absolutePath)
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

    }

    fun cleanupRecording() {
        (videoPathsList + transcodedVideoPathsList).forEach { path ->
            val f = File(path)
            if (f.exists()) {
                f.delete()
            }
        }
    }

    fun cleanupDecoration() {

    }

    val streams = mutableListOf<Int>()
    val initialVolumes = mutableListOf<Int>()

    private fun setMuteAll(mute: Boolean) {
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (streams.isEmpty()) {
            streams.add(AudioManager.STREAM_SYSTEM)
            streams.add(AudioManager.STREAM_MUSIC)
        }
        if (mute) {
            initialVolumes.clear()
            for (stream in streams) {
                initialVolumes.add(manager.getStreamVolume(stream))
                manager.setStreamVolume(stream, 0, 0)
            }
        } else {
            if (initialVolumes.size == streams.size) {
                var index = 0
                for (stream in streams) {
                    manager.setStreamVolume(stream, initialVolumes[index], 0)
                    ++index
                }
            }
        }
    }

}
