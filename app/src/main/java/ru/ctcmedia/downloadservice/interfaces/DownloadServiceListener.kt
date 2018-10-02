package ru.ctcmedia.downloadservice.interfaces

interface DownloadServiceListener {
    fun onStart(downloadableID: Long)
    fun onProgress(downloadableID: Long, progress: Int)
    fun onPause(downloadableID: Long)
    fun onError(downloadableID: Long)
    fun onFinish(downloadableID: Long)
}