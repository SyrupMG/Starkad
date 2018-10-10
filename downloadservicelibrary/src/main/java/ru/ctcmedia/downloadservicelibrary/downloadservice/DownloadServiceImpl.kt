@file:Suppress("DEPRECATION")

package ru.ctcmedia.downloadservicelibrary.downloadservice

import android.app.IntentService
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.support.v4.content.ContextCompat
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import com.tonyodev.fetch2.FetchListener
import com.tonyodev.fetch2.Request
import com.tonyodev.fetch2.Status.QUEUED
import com.tonyodev.fetch2.util.DEFAULT_GROUP_ID
import com.tonyodev.fetch2core.DownloadBlock
import com.tonyodev.fetch2core.Extras
import com.tonyodev.fetch2core.Func
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.DownloadStatusListener
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.Downloadable
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.downloadable
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.Settings
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.settingsNetworkType
import java.io.File

typealias DownloadNotificationDescription = Pair<String?, String?>

class DownloadNotification(val iconId: Int, val progress: (downloadableName: String) -> DownloadNotificationDescription)

object DownloadService : android.os.Binder() {

    private val runners = arrayListOf<DownloadService.() -> Unit>()

    fun onReady(callback: DownloadService.() -> Unit) {
        service?.run { callback.invoke(this@DownloadService) } ?: runners.add(callback)
    }

    private val downloadableListeners = mutableMapOf<String, ArrayList<DownloadStatusListener>>()

    fun notifyWith(notificationDescription: () -> DownloadNotification) {
        notificationSettings = notificationDescription()
    }

    internal lateinit var notificationSettings: DownloadNotification

    var configuration: Settings = Settings()
        set(value) {
            if (field == value) return

            field = value
            service?.config = value
        }

    var service: DownloadServiceImpl? = null
    private lateinit var serviceConnection: ServiceConnection

    fun Context.bindContext() {
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                configuration = service!!.config
                service!!.facade = this@DownloadService

                runners.forEach { this@DownloadService.it() }
                runners.clear()
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                service = null
            }
        }

        val intent = Intent(this, DownloadServiceImpl::class.java)
        ContextCompat.startForegroundService(this, intent)
        applicationContext.bindService(intent, serviceConnection, 0)
    }

    fun Context.unbindContext() {
        applicationContext.unbindService(serviceConnection)
    }

    internal fun download(downloadable: Downloadable) {
        onReady {
            service?.resume(downloadable)
        }
        downloadableListeners[downloadable.mixedUniqueId]?.apply {
            forEach { it.downloadBegan() }
        }
    }

    internal fun cancel(downloadable: Downloadable) {
        downloadableListeners[downloadable.mixedUniqueId]?.apply {
            forEach { it.downloadFinished() }
        }
        onReady {
            service?.cancel(downloadable)
        }
    }

    internal fun progressFor(downloadable: Downloadable, callback: (Double) -> Unit) {
        onReady {
            service?.progressFor(downloadable, callback)
        }
    }

    fun onStart(downloadableId: String) {
        downloadableListeners[downloadableId]?.apply {
            forEach { it.downloadBegan() }
        }
    }

    fun onProgress(downloadableId: String, progress: Int) {
        downloadableListeners[downloadableId]?.apply {
            forEach { it.downloadProgressUpdate(progress / 100.0) }
        }
    }

    fun onError(downloadableId: String) {
        downloadableListeners[downloadableId]?.apply {
            forEach { it.downloadFailed(Error("Ошибка")) }
        }
    }

    fun onFinish(downloadableId: String) {
        downloadableListeners[downloadableId]?.apply {
            forEach { it.downloadFinished() }
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

    companion object {
        private const val downloadNameKey = "downloadNameKey"
    }

    var facade: DownloadService? = null
        set(value) {
            field = value
            fetch.resumeGroup(DEFAULT_GROUP_ID)
        }

    fun resume(downloadable: Downloadable) {
        val request = Request(downloadable.remoteUrl.toString(), downloadable.localUrl.downloadable().toString())
        request.tag = downloadable.mixedUniqueId
        downloadable.downloadableName?.also { request.extras = Extras(mapOf(downloadNameKey to it)) }
        fetch.enqueue(request, null, null)
    }

    var config: Settings
        get() {
            return Settings(fetchConfig.concurrentLimit, fetchConfig.globalNetworkType.settingsNetworkType())
        }
        set(value) {
            fetch.pauseGroup(DEFAULT_GROUP_ID)
            fetch.close()

            fetchConfig = FetchConfiguration.Builder(this)
                .setDownloadConcurrentLimit(value.concurrentDownloads)
                .setGlobalNetworkType(value.networkType.fetchNetworkType())
                .build()

            fetch.resumeGroup(DEFAULT_GROUP_ID)
        }

    private lateinit var fetchConfig: FetchConfiguration
    val fetch
        get() = Fetch.getInstance(fetchConfig).also { it.addListener(this) }

    override fun onBind(intent: Intent?): IBinder? {
        fetchConfig = FetchConfiguration.Builder(this).build()
        return DownloadService.apply { service = this@DownloadServiceImpl }
    }

    fun cancel(downloadable: Downloadable) {
        getDownload(downloadable.mixedUniqueId) {
            this ?: return@getDownload
            fetch.cancel(id)
            notificationManager.cancel(id)
        }
    }

    private fun getDownload(uniqueId: String, callback: Download?.() -> Unit) {
        fetch.getDownloads(Func { list ->
            callback(list.firstOrNull { it.tag == uniqueId })
        })
    }

    fun progressFor(downloadable: Downloadable, callback: (Double) -> Unit) {
        getDownload(downloadable.mixedUniqueId) {
            this ?: return@getDownload callback(0.0)
            callback(progress / 100.0)
        }
    }

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private val foregroundNotificationId = 1

    override fun onAdded(download: Download) {
    }

    override fun onCancelled(download: Download) {}

    override fun onCompleted(download: Download) {
        val fileName = Uri.parse(download.file)
        val from = File(fileName.downloadable().path)
        val to = File(fileName.path)
        from.renameTo(to)
        notificationManager.cancel(download.id)
        facade?.onFinish(download.tag ?: "")
        checkNeedStop()
    }

    private fun checkNeedStop() {
        (fetch.getDownloadsWithStatus(QUEUED, Func {
            if (it.isEmpty()) stopSelf()
        }))
    }

    override fun onDeleted(download: Download) {}

    override fun onDownloadBlockUpdated(download: Download, downloadBlock: DownloadBlock, totalBlocks: Int) {}

    override fun onError(download: Download, error: com.tonyodev.fetch2.Error, throwable: Throwable?) {
        facade?.onError(download.tag ?: "")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(foregroundNotificationId, Notification.Builder(this).build())
        return START_STICKY
    }

    override fun onHandleIntent(intent: Intent?) {}

    override fun onPaused(download: Download) {
    }

    override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
        facade?.run {
            notificationManager.notify(download.id, notification(download, download.progress, notificationSettings))
            onProgress(download.tag ?: "", download.progress)
        }
    }

    override fun onQueued(download: Download, waitingOnNetwork: Boolean) {}

    override fun onRemoved(download: Download) {}

    override fun onResumed(download: Download) {}

    override fun onStarted(download: Download, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) {
        facade?.onStart(download.tag ?: "")
    }

    override fun onWaitingNetwork(download: Download) {}

    override fun onDestroy() {
        super.onDestroy()
        notificationManager.cancelAll()
        stopForeground(true)
    }

    private val builder by lazy { Notification.Builder(this@DownloadServiceImpl) }
    private fun notification(downloadable: Download, progress: Int, notificationDescription: DownloadNotification): Notification {
        val texts = downloadable.extras.getString(downloadNameKey, "").let { notificationDescription.progress(it) }
        return builder
            .setSmallIcon(notificationDescription.iconId)
            .setContentTitle(texts.first)
            .setContentText(texts.second)
            .setProgress(100, progress, false)
            .build()
    }
}