package ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces



interface DownloadStatusListener {
    fun downloadStart()
    fun downloadOnProgress(progress: Int)
    fun downloadFinish()
    fun downloadError()
}