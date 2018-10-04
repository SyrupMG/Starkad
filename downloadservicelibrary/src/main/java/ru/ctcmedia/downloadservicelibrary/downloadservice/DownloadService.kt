package ru.ctcmedia.downloadservicelibrary.downloadservice

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.support.v4.content.ContextCompat
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
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.DownloadStatusListener
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.Downloadable
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.downloadable
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.Settings
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.settingsNetworkType
import ru.ctcmedia.downloadservicelibrary.notify
import ru.ctcmedia.downloadservicelibrary.register

private interface DownloadServiceListener {
    fun onStart(downloadableId: String)
    fun onProgress(downloadableId: String, progress: Int)
    fun onError(downloadableId: String)
    fun onFinish(downloadableId: String)
}

object DownloadServiceFacade : DownloadServiceListener {

    private val downloadableListeners = mutableMapOf<String, ArrayList<DownloadStatusListener>>()

    var configuration: Settings
        get() {
            var value = Settings()
            Broadcaster.notify<ActionsListener> { value = getSettings() }
            return value
        }
        set(value) {
            Broadcaster.notify<ActionsListener> { setSettings(value) }
        }

    init {
        Broadcaster.register<DownloadServiceListener>(this)
    }

    fun Context.bindContext() {
        val intent = Intent(this, DownloadService::class.java)
        ContextCompat.startForegroundService(this, intent)
        Broadcaster.notify<ActionsListener> { setSettings(configuration) }
    }

    internal fun download(downloadable: Downloadable) {
        Broadcaster.notify<ActionsListener> { resume(downloadable) }
    }

    internal fun cancel(downloadable: Downloadable) {
        Broadcaster.notify<ActionsListener> { cancel(downloadable) }
    }

    fun current(callback: (List<Download>?) -> Unit) {
        Broadcaster.notify<ActionsListener> { current { callback(it) } }
    }

    override fun onStart(downloadableId: String) {
        downloadableListeners[downloadableId]?.apply {
            forEach { it.downloadStart() }
        }
    }

    override fun onProgress(downloadableId: String, progress: Int) {
        downloadableListeners[downloadableId]?.apply {
            forEach { it.downloadOnProgress(progress) }
        }
    }

    override fun onError(downloadableId: String) {
        downloadableListeners[downloadableId]?.apply {
            forEach { it.downloadError() }
        }
    }

    override fun onFinish(downloadableId: String) {
        downloadableListeners[downloadableId]?.apply {
            forEach { it.downloadFinish() }
        }
    }

    fun register(listener: DownloadStatusListener, `for`: Downloadable) {
        val id = `for`.mixedUniqueId
        val receivers = downloadableListeners[id] ?: arrayListOf<DownloadStatusListener>().also { downloadableListeners[id] = it }
        receivers.add(listener)
    }

    fun unregister(listener: DownloadStatusListener, `for`: Downloadable) {
        val id = `for`.mixedUniqueId
        downloadableListeners[id]?.remove(listener)
        // remove if empty
    }
}

private val Downloadable.mixedUniqueId: String
    get() = "${this.javaClass.simpleName}||$downloadableUniqueId"

class DownloadService : Service(), FetchListener, ActionsListener {

    override fun resume(downloadable: Downloadable) {
        val request = Request(downloadable.remoteUrl.toString(), downloadable.localUrl.downloadable().toString())
        map[request.id] = downloadable.downloadableUniqueId
        fetch.enqueue(request, null, null)
    }

    private val TAG = DownloadService::class.java.simpleName
    private var config: Settings
        get() = Settings(fetchConfig.concurrentLimit, fetchConfig.globalNetworkType.settingsNetworkType())
        set(value) {
            fetchConfig = FetchConfiguration.Builder(this)
                .setDownloadConcurrentLimit(value.concurrentDownloads)
                .setGlobalNetworkType(value.networkType.fetchNetworkType())
                .build()
        }

    private lateinit var fetchConfig: FetchConfiguration
    private val fetch
        get() = Fetch.getInstance(fetchConfig)

    init {
        Broadcaster.register<ActionsListener>(this)
    }

    override fun onBind(intent: Intent?): IBinder? = DownloadBinder()

    override fun setSettings(settings: Settings) {
        if (::fetchConfig.isInitialized) {
            fetch.close()
        }

        config = settings
        fetch.addListener(this)
//
//        fetch.getDownloads(Func { list ->
//            val idList = list.map { it.id }
//            fetch.pause(idList)
//            fetch.close()
//
//            config = settings
//
//            fetch.resume(idList)
//        })
    }

    override fun getSettings() = config

    override fun cancel(downloadable: Downloadable) {
        fetch.getDownloads(Func { list ->
            val downloadableKey = map.filter { it.value == downloadable.mixedUniqueId }.keys.first()
            val download = list.asSequence().firstOrNull { map[downloadableKey] == downloadable.downloadableUniqueId }
            download?.let {
                fetch.cancel(it.id)
                notificationManager.cancel(download.id)
            }
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

    private val map = mutableMapOf<Int, String>()

    private val foregroundNotificationId = 1

    override fun onAdded(download: Download) {
        Log.d(TAG, "OnAdded")
    }

    override fun onCancelled(download: Download) {
        Log.d(TAG, "OnCancelled")
    }

    override fun onCompleted(download: Download) {
        val currentDownloadable = map[download.id] ?: return
        val notification = notificationBuilder
            .setContentTitle("Файл скачан")
            .setOngoing(false)
            .build()
        notificationManager.notify(download.id, notification)
        Broadcaster.notify<DownloadServiceListener> { onFinish(currentDownloadable) }
    }

    override fun onDeleted(download: Download) {
        Log.d(TAG, "OnDeleted")
    }

    override fun onDownloadBlockUpdated(download: Download, downloadBlock: DownloadBlock, totalBlocks: Int) {
        Log.d(TAG, "OnDownloadBlockUpdated")
    }

    override fun onError(download: Download, error: Error, throwable: Throwable?) {
        val currentDownloadable = map[download.id] ?: return
        val notification = notificationBuilder
            .setContentTitle("Ошибка!")
            .setOngoing(false)
            .build()
        notificationManager.notify(download.id, notification)
        Broadcaster.notify<DownloadServiceListener> { onError(currentDownloadable) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(foregroundNotificationId, notificationBuilder.build())
        return START_STICKY
    }

    override fun onPaused(download: Download) {
    }

    override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
        val currentDownloadable = map[download.id] ?: return
        notificationManager.notify(
            download.id, notificationBuilder
                .setProgress(100, download.progress, false)
                .setContentText("${download.progress} из 100")
                .setOngoing(true)
                .build()
        )

        Broadcaster.notify<DownloadServiceListener> { onProgress(currentDownloadable, download.progress) }
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
        val currentDownloadable = map[download.id] ?: return
        Broadcaster.notify<DownloadServiceListener> { onStart(currentDownloadable) }
    }

    override fun onWaitingNetwork(download: Download) {
        Log.d(TAG, "OnWaitingNetwork")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OnDestroy")
        stopForeground(true)
        notificationManager.cancelAll()
    }

    inner class DownloadBinder : Binder() {
        val service: DownloadService
            get() = this@DownloadService
    }
}
