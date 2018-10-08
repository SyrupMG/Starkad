package ru.ctcmedia

import android.app.Notification
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
import ru.ctcmedia.downloadservicelibrary.downloadservice.DownloadServiceFacade
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.DownloadStatusListener
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.Downloadable
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.cancelDownload
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.forget
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.observe
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.resumeDownload
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.NetworkType
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.NotificationSettings
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.Settings

class MainActivity : AppCompatActivity(), DownloadStatusListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DownloadServiceFacade.apply {
            configuration = Settings(2, NetworkType.Wifi)
            notificationSettings = NotificationSettings(
                Notification.Builder(this@MainActivity)
                    .setContentTitle("Скачивается...")
                    .setSmallIcon(android.R.drawable.ic_popup_sync),
                { builder, progress ->
                    builder
                        .setOngoing(true)
                        .setProgress(100, progress, false)
                        .setContentText("$progress из 100")
                        .setSmallIcon(android.R.drawable.ic_popup_sync).build()
                },
                Notification.Builder(this@MainActivity)
                    .setContentTitle("Ошибка!")
                    .setSmallIcon(android.R.drawable.ic_popup_sync)
                    .setOngoing(false),
                Notification.Builder(this@MainActivity)
                    .setContentTitle("Файл скачан")
                    .setSmallIcon(android.R.drawable.ic_popup_sync)
                    .setOngoing(false)
            )

            bindContext {
                val file = DownloadableFile("http://mirror.filearena.net/pub/speed/SpeedTest_16MB.dat", "${filesDir.path}/video/16mb.mp4")
                val bigFile = DownloadableFile("http://mirror.filearena.net/pub/speed/SpeedTest_128MB.dat", "${filesDir.path}/video/128mb.mp4")

                file.apply {
                    resumeDownload()
                    observe(this@MainActivity)
                }

                bigFile.resumeDownload()

                GlobalScope.launch {
                    delay(10000)
                    file.forget(this@MainActivity)
                    delay(10000)
                    file.cancelDownload()
                }
            }
        }
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
