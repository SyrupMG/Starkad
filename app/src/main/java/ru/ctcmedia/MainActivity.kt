package ru.ctcmedia

import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable.Creator
import android.support.v7.app.AppCompatActivity
import ru.ctcmedia.downloadservice.R
import ru.ctcmedia.downloadservicelibrary.downloadservice.DownloadServiceFacade
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.DownloadStatusListener
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.Downloadable
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.cancelDownload
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.forget
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.observe
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.resumeDownload
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity(), DownloadStatusListener {

    override fun onCreate(savedInstanceState: Bundle?) {

        DownloadServiceFacade.apply { bindContext() }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val file = DownloadableFile("http://mirror.filearena.net/pub/speed/SpeedTest_16MB.dat", "${filesDir.path}/video/16mb.mp4")
        val bigFile = DownloadableFile("http://mirror.filearena.net/pub/speed/SpeedTest_128MB.dat", "${filesDir.path}/video/128mb.mp4")
        file.apply {
            resumeDownload()
            observe(this@MainActivity)
        }
        bigFile.resumeDownload()

        Timer().schedule(object : TimerTask() {
            override fun run() {
                bigFile.cancelDownload()
            }
        }, 20000)

        Timer().schedule(object : TimerTask() {
            override fun run() {
                file.forget(this@MainActivity)
            }
        }, 10000)
    }

    // DownloadStatusListener

    override fun downloadStart() {
    }

    override fun downloadOnProgress(progress: Int) {
    }

    override fun downloadFinish() {
    }

    override fun downloadError() {
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
