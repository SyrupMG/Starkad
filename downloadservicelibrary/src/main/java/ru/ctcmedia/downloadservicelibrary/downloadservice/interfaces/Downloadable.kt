package ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces

import android.os.Parcelable
import ru.ctcmedia.downloadservicelibrary.downloadservice.DownloadServiceFacade

interface Downloadable : Parcelable {
    val downloadableUniqueId: Long
    val remoteUrl: String
    val localUrl: String
    fun download() {
        DownloadServiceFacade.download(this)
    }

    fun cancel() {
        DownloadServiceFacade.cancel(this)
    }

    fun resume() {
        DownloadServiceFacade.download(this)
    }

    fun pause() {
        DownloadServiceFacade.cancel(this)
    }
}