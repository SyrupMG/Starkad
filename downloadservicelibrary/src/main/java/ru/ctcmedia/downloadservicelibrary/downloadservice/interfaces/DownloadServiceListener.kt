package ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces

import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.FileDownloadProgress

/**
 * Interface describing events that can generate classes that inherit Downloadable
 */
interface DownloadStatusListener {
    /**
     * This method is called when downloading has started.
     */
    fun downloadBegan() {}

    /**
     * This method is called when the progress of the downloaded file changes.
     */
    fun downloadProgressUpdate(progress: FileDownloadProgress) {}

    /**
     * This method is called at the end of the file download.
     */
    fun downloadFinished() {}

    /**
     * This method is called when a file download ends with error.
     */
    fun downloadFailed(error: Error) {}
}