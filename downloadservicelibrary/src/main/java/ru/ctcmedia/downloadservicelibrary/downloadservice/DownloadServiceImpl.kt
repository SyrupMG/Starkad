package ru.ctcmedia.downloadservicelibrary.downloadservice

import android.app.IntentService
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.support.v4.content.ContextCompat
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import com.tonyodev.fetch2.FetchListener
import com.tonyodev.fetch2.Request
import com.tonyodev.fetch2.Status.DOWNLOADING
import com.tonyodev.fetch2.Status.QUEUED
import com.tonyodev.fetch2core.DownloadBlock
import com.tonyodev.fetch2core.Func
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.DownloadStatusListener
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.Downloadable
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.downloadable
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.Settings
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.settingsNetworkType

typealias DownloadNotificationDescription = Pair<String, String>

class DownloadNotification(val iconId: Int, val progress: (downloadableName: String) -> DownloadNotificationDescription)

object DownloadService : android.os.Binder() {

    private val downloadableListeners = mutableMapOf<String, ArrayList<DownloadStatusListener>>()

    lateinit var notificationSettings: DownloadNotification

    var configuration: Settings = Settings()
        set(value) {
            if (field == value) return

            field = value
            service?.config = value
        }

    var service: DownloadServiceImpl? = null
    private lateinit var serviceConnection: ServiceConnection

    fun Context.bindContext(callback: () -> Unit) {
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                configuration = service!!.config
                service!!.facade = this@DownloadService

                callback()
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                service = null
            }
        }

        val intent = Intent(this, DownloadServiceImpl::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, 0)
    }

    fun Context.unbindContext() {
        unbindService(serviceConnection)
    }

    internal fun download(downloadable: Downloadable) {
        service?.resume(downloadable)
    }

    internal fun cancel(downloadable: Downloadable) {
        service?.cancel(downloadable)
    }

    fun current(callback: (List<Download>?) -> Unit) {
        service?.current { callback(it) }
    }

    fun onStart(downloadableId: String) {
        downloadableListeners[downloadableId]?.apply {
            forEach { it.downloadStart() }
        }
    }

    fun onProgress(downloadableId: String, progress: Int) {
        downloadableListeners[downloadableId]?.apply {
            forEach { it.downloadOnProgress(progress) }
        }
    }

    fun onError(downloadableId: String) {
        downloadableListeners[downloadableId]?.apply {
            forEach { it.downloadError() }
        }
    }

    fun onFinish(downloadableId: String) {
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
    }

}

private val Downloadable.mixedUniqueId: String
    get() = "${this.javaClass.simpleName}||$downloadableUniqueId"

class DownloadServiceImpl : IntentService("Service"), FetchListener {

    var facade: DownloadService? = null

    fun resume(downloadable: Downloadable) {
        val request = Request(downloadable.remoteUrl.toString(), downloadable.localUrl.downloadable().toString())
        map[request.id] = downloadable
        fetch.enqueue(request, null, null)
    }


    var config: Settings
        get() {
            return Settings(fetchConfig.concurrentLimit, fetchConfig.globalNetworkType.settingsNetworkType())
        }
        set(value) {
            if (::fetchConfig.isInitialized) {
                fetch.close()
            }

            fetchConfig = FetchConfiguration.Builder(this)
                .setDownloadConcurrentLimit(value.concurrentDownloads)
                .setGlobalNetworkType(value.networkType.fetchNetworkType())
                .build()
        }

    private lateinit var fetchConfig: FetchConfiguration
    private val fetch
        get() = Fetch.getInstance(fetchConfig).also { it.addListener(this) }

    override fun onBind(intent: Intent?): IBinder? {
        fetchConfig = FetchConfiguration.Builder(this).build()
        return DownloadService.apply { service = this@DownloadServiceImpl }
    }

    fun cancel(downloadable: Downloadable) {
        fetch.getDownloads(Func { list ->
            val downloadableKey = map.filter { it.value == downloadable }.keys.firstOrNull()
            val download = list.asSequence().firstOrNull { map[downloadableKey] == downloadable }
            download?.let {
                fetch.cancel(it.id)
                notificationManager.cancel(download.id)
            }
        })
    }

    fun current(callback: (List<Download>?) -> Unit) {
        fetch.getDownloadsWithStatus(DOWNLOADING, Func { callback(it) })
    }

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private val map = mutableMapOf<Int, Downloadable>()

    private val foregroundNotificationId = 1

    override fun onAdded(download: Download) { }

    override fun onCancelled(download: Download) { }

    override fun onCompleted(download: Download) {
        val currentDownloadable = map[download.id] ?: return
        notificationManager.cancel(download.id)
        facade?.onFinish(currentDownloadable.mixedUniqueId)
        (fetch.getDownloadsWithStatus(QUEUED, Func {
            if (it.isEmpty()) stopSelf()
        }))
    }

    override fun onDeleted(download: Download) { }

    override fun onDownloadBlockUpdated(download: Download, downloadBlock: DownloadBlock, totalBlocks: Int) { }

    override fun onError(download: Download, error: Error, throwable: Throwable?) {
        val currentDownloadable = map[download.id] ?: return
        facade?.onError(currentDownloadable.mixedUniqueId)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(foregroundNotificationId, Notification.Builder(this).build())
        return START_STICKY
    }

    override fun onHandleIntent(intent: Intent?) {}

    override fun onPaused(download: Download) {
    }

    override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
        val currentDownloadable = map[download.id] ?: return

        notificationManager.notify(download.id, notification(currentDownloadable, download.progress, facade?.notificationSettings!!))
        facade?.onProgress(currentDownloadable.mixedUniqueId, download.progress)
    }

    override fun onQueued(download: Download, waitingOnNetwork: Boolean) { }

    override fun onRemoved(download: Download) { }

    override fun onResumed(download: Download) { }

    override fun onStarted(download: Download, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) {
        val currentDownloadable = map[download.id] ?: return
        facade?.onStart(currentDownloadable.mixedUniqueId)
    }

    override fun onWaitingNetwork(download: Download) { }

    override fun onDestroy() {
        super.onDestroy()
        notificationManager.cancelAll()
        stopForeground(true)
    }

    private val builder by lazy { Notification.Builder(this@DownloadServiceImpl) }
    private fun notification(downloadable: Downloadable, progress: Int, notificationDescription: DownloadNotification) : Notification {
        val texts = notificationDescription.progress(downloadable.downloadableName!!)
        return builder
            .setSmallIcon(notificationDescription.iconId)
            .setContentTitle(texts.first)
            .setContentText(texts.second)
            .setProgress(100, progress, false)
            .build()
    }
}