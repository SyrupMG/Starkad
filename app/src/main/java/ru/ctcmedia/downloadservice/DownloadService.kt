package ru.ctcmedia.downloadservice

import android.app.IntentService
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.v4.content.ContextCompat.startForegroundService
import android.util.Log
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import com.tonyodev.fetch2.FetchListener
import com.tonyodev.fetch2.Request
import com.tonyodev.fetch2.Status.DOWNLOADING
import com.tonyodev.fetch2core.DownloadBlock
import com.tonyodev.fetch2core.Func
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
        startForegroundService(context, intent)
    }

    fun cancel(downloadable: Downloadable) {
        Broadcaster.notify<ActionsListener> {
            this.cancel(downloadable)
            downloadableList.remove(downloadable)
        }
    }

    fun current(): Download {
        var result: Download? = null
        Broadcaster.notify<ActionsListener> {
            result = this.current()
        }

        return result!!
    }
}

class DownloadService : IntentService("DownloadService"), FetchListener, ActionsListener {

    private lateinit var fetchConfig: FetchConfiguration
    private lateinit var fetch: Fetch

    override fun downloadNext() {
    }

    override fun cancel(downloadable: Downloadable) {
        var download: Download? = null
        fetch.getDownloads(Func { list -> download = list.asSequence().firstOrNull { it.identifier == downloadable.downloadableUniqueId.toLong() } })
        val result = download ?: return
        fetch.cancel(result.id)
    }

    override fun current(): Download? {
        var result: Download? = null
        fetch.getDownloadsWithStatus(DOWNLOADING, Func { result = it.first() })
        return result
    }

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val notificationBuilder: Notification.Builder by lazy {
        Notification.Builder(this)
            .setContentTitle("Скачивание")
            .setContentText("Скачивается...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
    }

    override fun onCreate() {
        super.onCreate()
        fetchConfig = FetchConfiguration.Builder(this)
            .setDownloadConcurrentLimit(Settings.concurrentDownloads)
            .setGlobalNetworkType(Settings.networkType.value)
            .build()
        fetch = Fetch.getInstance(fetchConfig)

        Broadcaster.register(ActionsListener::class, this)
    }

    override fun onHandleIntent(intent: Intent) {
        fetch.addListener(this)

        val downloadable = intent.getParcelableExtra<Downloadable>(DOWNLOADABLE_TAG)
        val fileName = Uri.parse(downloadable.remoteUrl).lastPathSegment
        val request = Request(downloadable.remoteUrl, "${this.filesDir}/$fileName")
        request.identifier = downloadable.downloadableUniqueId.toLong()
        fetch.enqueue(request, null, null)
    }

    override fun onAdded(download: Download) {
        Log.d(DownloadService::class.java.simpleName, "OnAdded")
    }

    override fun onCancelled(download: Download) {
        Log.d(DownloadService::class.java.simpleName, "OnCancelled")
    }

    override fun onCompleted(download: Download) {
        val notification = notificationBuilder
            .setContentTitle("Файл скачан")
            .setOngoing(true)
            .build()
        notificationManager.notify(download.id, notification)
        Broadcaster.notify<DownloadServiceListener> { onFinish(download.id.toString()) }
    }

    override fun onDeleted(download: Download) {
        Log.d(DownloadService::class.java.simpleName, "OnDeleted")
    }

    override fun onDownloadBlockUpdated(download: Download, downloadBlock: DownloadBlock, totalBlocks: Int) {
        Log.d(DownloadService::class.java.simpleName, "OnDownloadBlockUpdated")
    }

    override fun onError(download: Download, error: Error, throwable: Throwable?) {
        val notification = notificationBuilder
            .setContentTitle("Ошибка!")
            .setOngoing(true)
            .build()
        notificationManager.notify(download.id, notification)
        Broadcaster.notify<DownloadServiceListener> { onError(download.id.toString()) }
    }

    override fun onPaused(download: Download) {
        Log.d(DownloadService::class.java.simpleName, "OnPaused")
        Broadcaster.notify<DownloadServiceListener> { onPause(download.id.toString()) }
    }

    override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
        notificationBuilder.setProgress(100, download.progress, false)
        notificationBuilder.setOngoing(false)
        notificationManager.notify(
            download.id, notificationBuilder
                .setContentText("${download.progress} из 100")
                .build()
        )
        Broadcaster.notify<DownloadServiceListener> { onProgress(download.id.toString(), download.progress) }
    }

    override fun onQueued(download: Download, waitingOnNetwork: Boolean) {
        Log.d(DownloadService::class.java.simpleName, "OnQueued")
    }

    override fun onRemoved(download: Download) {
        Log.d(DownloadService::class.java.simpleName, "OnRemoved")
    }

    override fun onResumed(download: Download) {
        Log.d(DownloadService::class.java.simpleName, "OnResumed")
    }

    override fun onStarted(download: Download, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) {
        Broadcaster.notify<DownloadServiceListener> { onStart(download.id.toString()) }
    }

    override fun onWaitingNetwork(download: Download) {
        Log.d(DownloadService::class.java.simpleName, "OnWaitingNetwork")
    }

//    override fun onDestroy() {
//        super.onDestroy()
//        Log.d(DownloadService::class.java.simpleName, "OnDestroy")
//        notificationManager.cancelAll()
//    }
}
