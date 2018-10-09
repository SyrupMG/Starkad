package ru.ctcmedia

import android.R.drawable
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable.Creator
import android.support.v7.app.AppCompatActivity
import android.util.Log
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import ru.ctcmedia.downloadservice.R
import ru.ctcmedia.downloadservicelibrary.downloadservice.DownloadNotification
import ru.ctcmedia.downloadservicelibrary.downloadservice.DownloadService
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.DownloadStatusListener
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.Downloadable
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.cancelDownload
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.forget
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.observe
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.resumeDownload
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.NetworkType.Wifi
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.Settings

class MainActivity : AppCompatActivity(), DownloadStatusListener {

    val file by lazy { DownloadableFile("http://mirror.filearena.net/pub/speed/SpeedTest_16MB.dat", "${filesDir.path}/video/16mb.mp4") }
    val bigFile by lazy { DownloadableFile("http://mirror.filearena.net/pub/speed/SpeedTest_128MB.dat", "${filesDir.path}/video/128mb.mp4") }

    private inner class DownloadWrapper(val file: DownloadableFile) : DownloadStatusListener {

        init {
            file.observe(this)
        }

        override fun downloadFinish() {
        }

        override fun downloadError() {
            // Implement your custom error handling
        }
    }

    private val files by lazy { arrayOf(file, bigFile).map { DownloadWrapper(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        files //read only

        DownloadService.apply {

            notificationSettings = DownloadNotification(drawable.ic_popup_sync) {
                "Идет загрузка..." to it
            }

            bindContext {
                file.apply {
                    resumeDownload()
                    observe(this@MainActivity)
                }

                bigFile.resumeDownload()

                GlobalScope.launch {
                    delay(10000)
                    file.forget(this@MainActivity)
                    delay(10000)
                    configuration = Settings(2, Wifi)
                }
            }
        }
    }

    override fun onDestroy() {
        DownloadService.apply { unbindContext() }
        super.onDestroy()
    }

    // DownloadStatusListener

    override fun downloadStart() {
        Log.d(TAG, "DownloadStart")
    }

    private val TAG = this::class.java.simpleName

    override fun downloadOnProgress(progress: Int) {
        Log.d(TAG, "DownloadProgress $progress")
    }

    override fun downloadFinish() {
        Log.d(TAG, "DownloadFinish")
    }

    override fun downloadError() {
        Log.d(TAG, "DownloadError")
    }

    // ---- DownloadStatusListener
}

class DownloadableFile : Downloadable {

    override val downloadableName: String?
        get() = remoteUrl.lastPathSegment

    constructor(remoteUrl: String, localUrl: String) {
        this.remoteUrl = Uri.parse(remoteUrl)
        this.localUrl = Uri.parse(localUrl)
    }

    constructor(parcel: Parcel) {
        remoteUrl = Uri.parse(parcel.readString())
        localUrl = Uri.parse(parcel.readString())
    }

    override val downloadableUniqueId: String
        get() = "$remoteUrl||$localUrl".hashCode().toString()

    override val remoteUrl: Uri
    override val localUrl: Uri

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(remoteUrl.path)
        parcel.writeString(localUrl.path)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Creator<DownloadableFile> {
        override fun createFromParcel(parcel: Parcel): DownloadableFile {
            return DownloadableFile(parcel)
        }

        override fun newArray(size: Int): Array<DownloadableFile?> {
            return arrayOfNulls(size)
        }
    }
}
