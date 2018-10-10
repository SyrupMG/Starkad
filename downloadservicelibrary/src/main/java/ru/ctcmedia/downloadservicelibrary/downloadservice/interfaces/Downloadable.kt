package ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces

import android.net.Uri
import android.os.Parcelable
import ru.ctcmedia.downloadservicelibrary.downloadservice.DownloadService
import java.io.File

fun Uri.downloadable(): Uri = Uri.parse(this.toString() + ".downloadable")

interface Downloadable : Parcelable {
    val downloadableUniqueId: String
    val remoteUrl: Uri
    val localUrl: Uri
    val downloadableName: String?
}

val Downloadable.isDownloadLocalFileExist: Boolean
    get() = File(localUrl.path).exists()

val Downloadable.isDownloading: Boolean
    get() = File(localUrl.downloadable().path).exists()

fun Downloadable.getProgress(callback: (Double) -> Unit) {
    DownloadService.progressFor(this, callback)
}

fun Downloadable.observe(listener: DownloadStatusListener) {
    DownloadService.register(listener, this)
}

fun Downloadable.forget(listener: DownloadStatusListener) {
    DownloadService.unregister(listener, this)
}

fun Downloadable.resumeDownload() {
    DownloadService.download(this)
}

fun Downloadable.cancelDownload() {
    DownloadService.cancel(this)
}
