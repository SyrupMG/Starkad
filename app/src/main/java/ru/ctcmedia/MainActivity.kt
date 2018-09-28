package ru.ctcmedia

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable.Creator
import android.support.v7.app.AppCompatActivity
import android.util.Log
import ru.ctcmedia.downloadservice.DownloadServiceFacade
import ru.ctcmedia.downloadservice.R
import ru.ctcmedia.downloadservice.interfaces.DownloadServiceListener
import ru.ctcmedia.downloadservice.interfaces.Downloadable
import ru.ctcmedia.downloadservice.settings.Settings
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Settings.context = { applicationContext }
        val file = File("http://mirror.filearena.net/pub/speed/SpeedTest_256MB.dat")
        val file1 = File("http://mirror.filearena.net/pub/speed/SpeedTest_128MB.dat")
        file.download()
        file1.download()

        Timer().schedule(object : TimerTask() {
            override fun run() {
                DownloadServiceFacade.cancel(file)
            }
        }, 10000)
    }
}

class File() : Downloadable, DownloadServiceListener {

    private val simpleName = this::class.java.simpleName

    init {
        Broadcaster.register<DownloadServiceListener>(this)
    }

    override var downloadableUniqueId: Int = super.downloadableUniqueId
    override var remoteUrl: String = ""
    override var localUrl: String = ""

    constructor(url: String) : this() {
        remoteUrl = url
    }

    constructor(parcel: Parcel) : this() {
        remoteUrl = parcel.readString()
        localUrl = parcel.readString()
        downloadableUniqueId = parcel.readInt()
    }

    override fun onStart(downloadableID: String) {
        Log.d(simpleName, "onStart")
    }

    override fun onProgress(downloadableID: String, progress: Int) {
        Log.d(simpleName, "onProgress $downloadableID $progress")
    }

    override fun onPause(downloadableID: String) {
        Log.d(simpleName, "onPause")
    }

    override fun onError(downloadableID: String) {
        Log.d(simpleName, "onError")
    }

    override fun onFinish(downloadableID: String) {
        Log.d(simpleName, "onFinish")
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(remoteUrl)
        parcel.writeString(localUrl)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Creator<File> {
        override fun createFromParcel(parcel: Parcel): File {
            return File(parcel)
        }

        override fun newArray(size: Int): Array<File?> {
            return arrayOfNulls(size)
        }
    }
}
