package ru.ctcmedia.downloadservicelibrary.downloadservice

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
import ru.ctcmedia.downloadservicelibrary.Broadcaster
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.DownloadServiceListener
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.Downloadable
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.Settings
import ru.ctcmedia.downloadservicelibrary.notify
import ru.ctcmedia.downloadservicelibrary.register

const val DOWNLOADABLE_TAG = "downloadable_to_service"

object DownloadServiceFacade : DownloadServiceListener {

    init {
        Broadcaster.register<DownloadServiceListener>(this)
    }

    private val downloadableList = arrayListOf<Downloadable>()

    fun download(downloadable: Downloadable) {
        val context = Settings.context?.invoke() ?: return
        val intent = Intent(context, DownloadService::class.java).apply {
            putExtra(DOWNLOADABLE_TAG, downloadable)
        }
        downloadableList.add(downloadable)
        startForegroundService(context, intent)
    }

    fun cancel(downloadable: Downloadable) {
        Broadcaster.notify<ActionsListener> {
            cancel(downloadable)
            downloadableList.remove(downloadable)
        }
    }

    fun current(callback: (List<Download>?) -> Unit) {
        Broadcaster.notify<ActionsListener> { current { callback(it) } }
    }

    fun reinit() = Broadcaster.notify<ActionsListener> { reinit() }

    override fun onStart(downloadableID: Long) {}

    override fun onProgress(downloadableID: Long, progress: Int) {}

    override fun onPause(downloadableID: Long) {}

    override fun onError(downloadableID: Long) {}

    override fun onFinish(downloadableID: Long) {
        val downloadable = downloadableList.firstOrNull { it.downloadableUniqueId == downloadableID } ?: return
        downloadableList.remove(downloadable)
        if (downloadableList.isEmpty()) {
            val context = Settings.context?.invoke() ?: return
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }
}

class DownloadService private constructor() : IntentService("DownloadService"), FetchListener, ActionsListener {

    companion object {
        private val TAG = DownloadService::class.java.simpleName
    }

    private lateinit var fetchConfig: FetchConfiguration
    private lateinit var fetch: Fetch

    override fun reinit() {
        fetch.getDownloads(Func { list ->
            val idList = list.map { it.id }
            fetch.pause(idList)
            fetch.close()
            fetchConfig = FetchConfiguration.Builder(this)
                .setDownloadConcurrentLimit(Settings.concurrentDownloads)
                .setGlobalNetworkType(Settings.networkType.value)
                .build()
            fetch = Fetch.getInstance(fetchConfig)
            fetch.resume(idList)
        })
    }

    override fun cancel(downloadable: Downloadable) {
        fetch.getDownloads(Func { list ->
            val download = list.asSequence().firstOrNull { it.identifier == downloadable.downloadableUniqueId }
            download?.let { fetch.cancel(it.id) }
        })
    }

    override fun current(callback: (List<Download>?) -> Unit) {
        fetch.getDownloadsWithStatus(DOWNLOADING, Func { callback(it) })
    }

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val notificationBuilder: Notification.Builder by lazy {
        Notification.Builder(this)
            .setContentTitle("Скачивание")
            .setContentText("Скачивается...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Broadcaster.register<ActionsListener>(this)
        startForeground(1, notificationBuilder.build())

        fetchConfig = FetchConfiguration.Builder(this)
            .setDownloadConcurrentLimit(Settings.concurrentDownloads)
            .setGlobalNetworkType(Settings.networkType.value)
            .build()
        fetch = Fetch.getInstance(fetchConfig)
        fetch.addListener(this)

        return START_STICKY
    }

    override fun onHandleIntent(intent: Intent) {
        val downloadable = intent.getParcelableExtra<Downloadable>(DOWNLOADABLE_TAG)
        val fileName = Uri.parse(downloadable.remoteUrl).lastPathSegment
        val request = Request(downloadable.remoteUrl, "${this.filesDir}${downloadable.localUrl}/$fileName")
        request.identifier = downloadable.downloadableUniqueId
        fetch.enqueue(request, null, null)
    }

    override fun onAdded(download: Download) {
        Log.d(TAG, "OnAdded")
    }

    override fun onCancelled(download: Download) {
        Log.d(TAG, "OnCancelled")
    }

    override fun onCompleted(download: Download) {
        val notification = notificationBuilder
            .setContentTitle("Файл скачан")
            .setOngoing(false)
            .build()
        notificationManager.notify(1, notification)
        Broadcaster.notify<DownloadServiceListener> { onFinish(download.identifier) }
    }

    override fun onDeleted(download: Download) {
        Log.d(TAG, "OnDeleted")
    }

    override fun onDownloadBlockUpdated(download: Download, downloadBlock: DownloadBlock, totalBlocks: Int) {
        Log.d(TAG, "OnDownloadBlockUpdated")
    }

    override fun onError(download: Download, error: Error, throwable: Throwable?) {
        val notification = notificationBuilder
            .setContentTitle("Ошибка!")
            .setOngoing(false)
            .build()
        notificationManager.notify(1, notification)
        Broadcaster.notify<DownloadServiceListener> { onError(download.identifier) }
    }

    override fun onPaused(download: Download) {
        Log.d(TAG, "OnPaused")
        Broadcaster.notify<DownloadServiceListener> { onPause(download.identifier) }
    }

    override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
        notificationManager.notify(
            1, notificationBuilder
                .setProgress(100, download.progress, false)
                .setContentText("${download.progress} из 100")
                .setOngoing(true)
                .build()
        )
        Broadcaster.notify<DownloadServiceListener> { onProgress(download.identifier, download.progress) }
    }

    override fun onQueued(download: Download, waitingOnNetwork: Boolean) {
        Log.d(TAG, "OnQueued")
    }

    override fun onRemoved(download: Download) {
        Log.d(TAG, "OnRemoved")
    }

    override fun onResumed(download: Download) {
        Log.d(TAG, "OnResumed")
    }

    override fun onStarted(download: Download, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) {
        Broadcaster.notify<DownloadServiceListener> { onStart(download.identifier) }
    }

    override fun onWaitingNetwork(download: Download) {
        Log.d(TAG, "OnWaitingNetwork")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OnDestroy")
        notificationManager.cancelAll()
    }
}
