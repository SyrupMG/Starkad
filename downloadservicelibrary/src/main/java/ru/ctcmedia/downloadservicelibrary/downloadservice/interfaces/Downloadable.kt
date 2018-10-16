package ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import ru.ctcmedia.downloadservicelibrary.downloadservice.DownloadService
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.FileDownloadProgress
import java.io.File

/**
 * Расширение добавляющее .downloadable к uri
 */
fun Uri.downloadable(): Uri = Uri.parse(toString() + ".downloadable")

/**
 * Interface that must implement classes that can be downloaded
 * Allows you to subscribe to objects of this interface.
 * can start download
 * cancel download
 * check download status
 */
interface Downloadable : Parcelable {
    /**
     * Unique object id
     */
    val downloadableUniqueId: String

    /**
     * Uri showing where to download the file
     */
    val remoteUrl: Uri

    /**
     * Uri indicating the path where to save the file
     */
    val localUrl: Uri

    /**
     * Stored on disk file name
     */
    val downloadableName: String?
}

/**
 * Check local file existing or not
 */
val Downloadable.isDownloadLocalFileExist: Boolean
    get() = File(localUrl.path).exists()

/**
 * Check for the existence of a temporary file
 */
val Downloadable.isDownloading: Boolean
    get() = File(localUrl.downloadable().path).exists()

/**
 * Getting the progress of the downloaded file
 */
fun Downloadable.getProgress(callback: (FileDownloadProgress) -> Unit) {
    DownloadService.progressFor(this, callback)
}

/**
 * Method to provide subscription to object events
 */
infix fun DownloadStatusListener.observe(downloadable: Downloadable?) {
    downloadable ?: return
    DownloadService.register(this, downloadable)
}

/**
 * Method for unsubscribing object events
 */
infix fun DownloadStatusListener.forget(downloadable: Downloadable?) {
    downloadable ?: return
    DownloadService.unregister(this, downloadable)
}

/**
 * The method starts / resumes loading this object.
 */
infix fun Context.resume(downloadable: Downloadable?) {
    downloadable ?: return
    with(DownloadService) {
        download(downloadable)
    }
}

/**
 * The method cancels the download
 */
infix fun Context.cancel(downloadable: Downloadable?) {
    downloadable ?: return
    with(DownloadService) {
        cancel(downloadable)
    }
}