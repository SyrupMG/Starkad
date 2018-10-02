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
import java.util.Random
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Settings.context = { this }
        val file = File("http://mirror.filearena.net/pub/speed/SpeedTest_16MB.dat", "/video")
        val file1 = File("http://mirror.filearena.net/pub/speed/SpeedTest_128MB.dat", "/video")
        file.download()
        file1.download()

        Timer().schedule(object : TimerTask() {
            override fun run() {
                DownloadServiceFacade.cancel(file1)
            }
        }, 5000)

        Timer().schedule(object : TimerTask() {
            override fun run() {
                DownloadServiceFacade.current {
                    it
                }
            }
        }, 10000)
    }
}

class File() : Downloadable, DownloadServiceListener {

    constructor(remoteUrl: String, localUrl: String): this() {
        this.remoteUrl = remoteUrl
        this.localUrl = localUrl
    }

    constructor(parcel: Parcel) : this() {
        remoteUrl = parcel.readString() as String
        localUrl = parcel.readString() as String
        downloadableUniqueId = parcel.readLong()
    }

    override var remoteUrl: String = ""
    override var localUrl: String = ""

    private val simpleName = this::class.java.simpleName

    init {
        Broadcaster.register<DownloadServiceListener>(this)
    }

    override var downloadableUniqueId: Long = Random().nextLong()
        private set

    override fun onStart(downloadableID: Long) {
        Log.d(simpleName, "onStart")
    }

    override fun onProgress(downloadableID: Long, progress: Int) {
        Log.d(simpleName, "onProgress $downloadableID $progress")
    }

    override fun onPause(downloadableID: Long) {
        Log.d(simpleName, "onPause")
    }

    override fun onError(downloadableID: Long) {
        Log.d(simpleName, "onError")
    }

    override fun onFinish(downloadableID: Long) {
        Log.d(simpleName, "onFinish")
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(remoteUrl)
        parcel.writeString(localUrl)
        parcel.writeLong(downloadableUniqueId)
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
