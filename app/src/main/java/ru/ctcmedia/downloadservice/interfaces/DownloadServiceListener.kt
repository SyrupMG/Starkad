package ru.ctcmedia.downloadservice.interfaces

interface DownloadServiceListener {
    fun onStart(downloadableID: String)
    fun onProgress(downloadableID: String, progress: Int)
    fun onPause(downloadableID: String)
    fun onError(downloadableID: String)
    fun onFinish(downloadableID: String)
}