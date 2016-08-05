package me.skonb.texturecamera

import android.os.Environment

/**
 * Created by skonb on 2016/08/02.
 */
object CONSTANTS {

    val METADATA_REQUEST_BUNDLE_TAG = "requestMetaData"
    val FILE_START_NAME = "VMS_"
    val VIDEO_EXTENSION = ".mp4"
    val DCIM_FOLDER = "/DCIM"
    val CAMERA_FOLDER = "/Camera/Mizica"
    val TEMP_FOLDER = "/Temp"
    val CAMERA_FOLDER_PATH = Environment.getExternalStorageDirectory().toString() + CONSTANTS.DCIM_FOLDER + CONSTANTS.CAMERA_FOLDER
    val TEMP_FOLDER_PATH = Environment.getExternalStorageDirectory().toString() + CONSTANTS.DCIM_FOLDER + CONSTANTS.CAMERA_FOLDER + CONSTANTS.TEMP_FOLDER
    val VIDEO_CONTENT_URI = "content://media/external/video/media"

    val KEY_DELETE_FOLDER_FROM_SDCARD = "deleteFolderFromSDCard"

    val RECEIVER_ACTION_SAVE_FRAME = "com.javacv.recorder.intent.action.SAVE_FRAME"
    val RECEIVER_CATEGORY_SAVE_FRAME = "com.javacv.recorder"
    val TAG_SAVE_FRAME = "saveFrame"

    val RESOLUTION_HIGH = 1300
    val RESOLUTION_MEDIUM = 500
    val RESOLUTION_LOW = 180

    val RESOLUTION_HIGH_VALUE = 2
    val RESOLUTION_MEDIUM_VALUE = 1
    val RESOLUTION_LOW_VALUE = 0
}
