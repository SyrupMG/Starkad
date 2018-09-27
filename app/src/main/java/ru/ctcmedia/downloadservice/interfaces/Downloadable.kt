package ru.ctcmedia.downloadservice.interfaces

import android.os.Parcelable
import ru.ctcmedia.downloadservice.DownloadServiceFacade

interface Downloadable : Parcelable {
    val downloadableUniqueId: Int
        get() = this.hashCode()
    val remoteUrl: String
    val localUrl: String
    fun download() { DownloadServiceFacade.download(this) }
    fun cancel() { DownloadServiceFacade.cancel(this) }
    fun resume() { DownloadServiceFacade.download(this) }
    fun pause() { DownloadServiceFacade.cancel(this) }
}