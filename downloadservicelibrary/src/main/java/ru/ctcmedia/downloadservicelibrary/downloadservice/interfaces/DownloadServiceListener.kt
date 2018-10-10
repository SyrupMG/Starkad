package ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces



interface DownloadStatusListener {
    fun downloadBegan() {}
    fun downloadProgressUpdate(progress: Double) {}
    fun downloadFinished() {}
    fun downloadFailed(error: Error) {}
}