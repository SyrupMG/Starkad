package ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces

import android.net.Uri
import android.os.Parcelable
import ru.ctcmedia.downloadservicelibrary.downloadservice.DownloadServiceFacade
import java.io.File

fun Uri.downloadable(): Uri = Uri.parse(this.toString() + ".downloadable")

interface Downloadable : Parcelable {
    val downloadableUniqueId: String
    val remoteUrl: Uri
    val localUrl: Uri
}

val Downloadable.isDownloadLocalFileExist: Boolean
    get() = File(localUrl.path).exists()

val Downloadable.isDownloading: Boolean
    get() = File(localUrl.downloadable().path).exists()


fun Downloadable.observe(listener: DownloadStatusListener) {
    DownloadServiceFacade.register(listener, this)
}

fun Downloadable.forget(listener: DownloadStatusListener) {
    DownloadServiceFacade.unregister(listener, this)
}

fun Downloadable.resumeDownload() {
    DownloadServiceFacade.download(this)
}

fun Downloadable.cancelDownload() {
    DownloadServiceFacade.cancel(this)
}
