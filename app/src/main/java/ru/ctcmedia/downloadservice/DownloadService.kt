package ru.ctcmedia.downloadservice

import android.app.IntentService
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.v4.content.ContextCompat
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import com.tonyodev.fetch2.FetchListener
import com.tonyodev.fetch2.Request
import com.tonyodev.fetch2core.DownloadBlock
import ru.ctcmedia.Broadcaster
import ru.ctcmedia.downloadservice.interfaces.DownloadServiceListener
import ru.ctcmedia.downloadservice.interfaces.Downloadable
import ru.ctcmedia.downloadservice.settings.Settings
import ru.ctcmedia.notify

const val DOWNLOADABLE_TAG = "downloadable_to_service"

object DownloadServiceFacade {

    private val downloadableList = arrayListOf<Downloadable>()

    fun download(downloadable: Downloadable) {
        val context = Settings.context?.invoke() ?: throw Exception()
        val intent = Intent(context, DownloadService::class.java).apply {
            putExtra(DOWNLOADABLE_TAG, downloadable)
        }
        downloadableList.add(downloadable)
        ContextCompat.startForegroundService(context, intent)
    }

    fun cancel(downloadable: Downloadable) {
        val context = Settings.context?.invoke() ?: throw Exception()
        downloadableList.clear()
        context.stopService(Intent(context, DownloadService::class.java))
    }
}

class DownloadService : IntentService("DownloadService"), FetchListener {

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val notificationBuilder: Notification.Builder by lazy {
        Notification.Builder(this)
            .setContentTitle("Скачивание")
            .setContentText("Скачивается...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
    }

    override fun onCreate() {
        super.onCreate()

        startForeground(1, notificationBuilder.build())
    }

    override fun onHandleIntent(intent: Intent) {
        val fetchConfig = FetchConfiguration.Builder(this)
            .setDownloadConcurrentLimit(Settings.concurrentDownloads)
            .setGlobalNetworkType(Settings.networkType.value)
            .build()
        val fetch = Fetch.getInstance(fetchConfig)
        fetch.addListener(this)

        val downloadable = intent.getParcelableExtra<Downloadable>(DOWNLOADABLE_TAG)
        val fileName = Uri.parse(downloadable.remoteUrl).lastPathSegment
        val request = Request(downloadable.remoteUrl, "${this.filesDir}/$fileName")
        fetch.enqueue(request, null, null)
    }

    override fun onAdded(download: Download) {
    }

    override fun onCancelled(download: Download) {
    }

    override fun onCompleted(download: Download) {
        val notification = notificationBuilder
            .setContentTitle("Файл скачан")
            .build()
        notificationManager.notify(download.id, notification)
        Broadcaster.notify<DownloadServiceListener> { onFinish(download.id.toString()) }
    }

    override fun onDeleted(download: Download) {
    }

    override fun onDownloadBlockUpdated(download: Download, downloadBlock: DownloadBlock, totalBlocks: Int) {
    }

    override fun onError(download: Download, error: Error, throwable: Throwable?) {
        val notification = notificationBuilder
            .setContentTitle("Ошибка!")
            .build()
        notificationManager.notify(download.id, notification)
        Broadcaster.notify<DownloadServiceListener> { onError(download.id.toString()) }
    }

    override fun onPaused(download: Download) {
        Broadcaster.notify<DownloadServiceListener> { onPause(download.id.toString()) }
    }

    override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
        notificationBuilder.setProgress(100, download.progress, false)
        notificationManager.notify(
            download.id, notificationBuilder
                .setContentText("${download.progress} из 100")
                .build()
        )
        Broadcaster.notify<DownloadServiceListener> { onProgress(download.id.toString(), download.progress) }
    }

    override fun onQueued(download: Download, waitingOnNetwork: Boolean) {
    }

    override fun onRemoved(download: Download) {
    }

    override fun onResumed(download: Download) {
    }

    override fun onStarted(download: Download, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) {
        Broadcaster.notify<DownloadServiceListener> { onStart(download.id.toString()) }
    }

    override fun onWaitingNetwork(download: Download) {
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationManager.cancelAll()
    }
}
