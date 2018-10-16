@file:Suppress("DEPRECATION")

package ru.ctcmedia.downloadservicelibrary.downloadservice

import android.app.IntentService
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
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
import com.tonyodev.fetch2.Status
import com.tonyodev.fetch2.Status.ADDED
import com.tonyodev.fetch2.Status.CANCELLED
import com.tonyodev.fetch2.Status.DELETED
import com.tonyodev.fetch2.Status.DOWNLOADING
import com.tonyodev.fetch2.Status.FAILED
import com.tonyodev.fetch2.Status.NONE
import com.tonyodev.fetch2.Status.PAUSED
import com.tonyodev.fetch2.Status.QUEUED
import com.tonyodev.fetch2.Status.REMOVED
import com.tonyodev.fetch2.provider.NetworkInfoProvider
import com.tonyodev.fetch2.util.DEFAULT_GROUP_ID
import com.tonyodev.fetch2core.DownloadBlock
import com.tonyodev.fetch2core.Extras
import com.tonyodev.fetch2core.Func
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.DownloadStatusListener
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.Downloadable
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.downloadable
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.FileDownloadProgress
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.Settings
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.settingsNetworkType
import ru.ctcmedia.downloadservicelibrary.lazyObserving
import java.io.File

private const val SETTINGS_INTENT_KEY = "settings"
private const val DOWNLOAD_NAME_KEY = "DOWNLOAD_NAME_KEY"
private const val NOTIFICATION_ID = 1337_0000

private val completedStatuses = arrayOf(Status.COMPLETED, CANCELLED, FAILED, REMOVED, DELETED)
private val pendingStatuses = arrayOf(Status.DOWNLOADING, PAUSED)
private val workingStatuses = pendingStatuses + arrayOf(QUEUED, ADDED, NONE)

/**
 * Класс описывающий уведомление при скачивании файла
 */
data class DownloadNotification(val iconId: Int, val activeTitle: String, val pendingTitle: String)

/**
 * Сервис в котором происходит вся работа
 */
object DownloadService : android.os.Binder() {

    /**
     * Параметры скачивания описываются классом Settings
     */
    var configuration: Settings = Settings()
        set(value) {
            if (field == value) return

            field = value
            service?.config = value
        }

    /**
     * Метод который отрабатывает при готовности сервиса к работе
     */
    fun onReady(callback: DownloadService.() -> Unit) {
        service?.run { callback.invoke(this@DownloadService) } ?: runners.add(callback)
    }

    /**
     * Метод назначающий уведомление на сервис
     */
    fun notifyWith(notificationDescription: DownloadNotification) {
        notificationSettings = notificationDescription
    }

    private val runners = arrayListOf<DownloadService.() -> Unit>()

    private val downloadableListeners = mutableMapOf<String, ArrayList<DownloadStatusListener>>()

    internal lateinit var notificationSettings: DownloadNotification

    internal var service: DownloadServiceImpl? = null
    private lateinit var serviceConnection: ServiceConnection

    /**
     * Метод, стартующий сервис, после данного метода отрабатывает onReady
     */
    fun Context.bindContext() {
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service!!.facade = this@DownloadService

                runners.forEach { this@DownloadService.it() }
                runners.clear()
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                service = null
            }
        }

        Intent(this, DownloadServiceImpl::class.java).also {
            it.putExtra(SETTINGS_INTENT_KEY, configuration)

            ContextCompat.startForegroundService(this, it)
            applicationContext.bindService(it, serviceConnection, 0)
        }
    }

    /**
     * Отвязка сервиса
     */
    fun Context.unbindContext() {
        applicationContext.unbindService(serviceConnection)
    }

    internal fun Context.download(downloadable: Downloadable) {
        bindContext()
        onReady {
            service?.resume(downloadable)
        }
        notifyListeners(downloadable) { it.downloadBegan() }
    }

    private fun notifyListeners(downloadable: Downloadable, callbackForEach: (DownloadStatusListener) -> Unit) {
        notifyListeners(downloadable.mixedUniqueId, callbackForEach)
    }

    private fun notifyListeners(downloadableId: String, callbackForEach: (DownloadStatusListener) -> Unit) {
        downloadableListeners[downloadableId]?.apply {
            forEach(callbackForEach)
        }
    }

    internal fun Context.cancel(downloadable: Downloadable) {
        bindContext()
        notifyListeners(downloadable) { it.downloadFinished() }
        onReady {
            service?.cancel(downloadable)
        }
    }

    internal fun progressFor(downloadable: Downloadable, callback: (FileDownloadProgress) -> Unit) {
        onReady {
            service?.progressFor(downloadable, callback)
        }
    }

    internal fun onStart(downloadableId: String) {
        notifyListeners(downloadableId) { it.downloadBegan() }
    }

    internal fun onProgress(downloadableId: String, progress: Int) {
        notifyListeners(downloadableId) { it.downloadProgressUpdate(FileDownloadProgress(progress / 100.0)) }
    }

    internal fun onError(downloadableId: String) {
        notifyListeners(downloadableId) { it.downloadFailed(Error("Ошибка")) }
    }

    internal fun onFinish(downloadableId: String) {
        notifyListeners(downloadableId) { it.downloadFinished() }
    }

    internal fun register(listener: DownloadStatusListener, `for`: Downloadable) {
        val id = `for`.mixedUniqueId
        val receivers = downloadableListeners[id] ?: arrayListOf<DownloadStatusListener>().also { downloadableListeners[id] = it }
        receivers.add(listener)
    }

    internal fun unregister(listener: DownloadStatusListener, `for`: Downloadable) {
        val id = `for`.mixedUniqueId
        downloadableListeners[id]?.remove(listener)
    }
}

private val Downloadable.mixedUniqueId: String
    get() = "${this.javaClass.simpleName}||$downloadableUniqueId"

internal class DownloadServiceImpl : IntentService("DownloadService"), FetchListener {
    private val networkInfoProvider = NetworkInfoProvider(this)
    private val networkBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateNotification()
    }

    init {
        networkInfoProvider.registerNetworkBroadcastReceiver(networkBroadcastReceiver)
    }

    var facade: DownloadService? = null
        set(value) {
            field = value
            fetch.resumeGroup(DEFAULT_GROUP_ID)
            organizeQueue()
        }

    fun resume(downloadable: Downloadable) {
        val request = Request(downloadable.remoteUrl.toString(), downloadable.localUrl.downloadable().toString())
        request.tag = downloadable.mixedUniqueId
        downloadable.downloadableName?.also { request.extras = Extras(mapOf(DOWNLOAD_NAME_KEY to it)) }

        fetch.enqueue(request)
    }

    internal var config: Settings
        get() {
            return Settings(fetch.fetchConfiguration.concurrentLimit, fetch.fetchConfiguration.globalNetworkType.settingsNetworkType())
        }
        set(value) {
            fetch.pauseGroup(DEFAULT_GROUP_ID)
            fetch.close()

            fetchConfig = value.fetchConfig()

            fetch.resumeGroup(DEFAULT_GROUP_ID)
            organizeQueue()
        }

    private fun Settings.fetchConfig() = FetchConfiguration.Builder(this@DownloadServiceImpl)
        .setDownloadConcurrentLimit(concurrentDownloads)
        .setGlobalNetworkType(networkType.fetchNetworkType())
        .build()

    private var fetchConfig: FetchConfiguration by lazyObserving({ FetchConfiguration.Builder(this).build() }, didSet = {
        if (::fetch.isInitialized && !fetch.isClosed) {
            fetch.removeListener(this)
        }
        fetch = Fetch.getInstance(fetchConfig)
        fetch.addListener(this)
    })
    lateinit var fetch: Fetch
        private set

    override fun onBind(intent: Intent?): IBinder? {
        fetchConfig = intent?.getParcelableExtra<Settings>(SETTINGS_INTENT_KEY)?.fetchConfig() ?: FetchConfiguration.Builder(this).build()
        return DownloadService.apply { service = this@DownloadServiceImpl }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, Notification.Builder(this).build())
        DownloadService.apply { service = this@DownloadServiceImpl }
        return START_STICKY
    }

    override fun onHandleIntent(intent: Intent?) {}

    fun cancel(downloadable: Downloadable) {
        getDownload(downloadable.mixedUniqueId) {
            this ?: return@getDownload
            fetch.cancel(id)
        }
    }

    private fun getDownload(uniqueId: String, callback: Download?.() -> Unit) {
        fetch.getDownloads(Func { list ->
            callback(list.firstOrNull { it.tag == uniqueId })
        })
    }

    fun progressFor(downloadable: Downloadable, callback: (FileDownloadProgress) -> Unit) {
        getDownload(downloadable.mixedUniqueId) {
            this ?: return@getDownload callback(FileDownloadProgress(0.0))
            callback(FileDownloadProgress(progress / 100.0))
        }
    }

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onAdded(download: Download) {
        organizeQueue()
    }

    override fun onCancelled(download: Download) {
        organizeQueue()
    }

    override fun onCompleted(download: Download) {
        val fileName = Uri.parse(download.file)
        val from = File(fileName.downloadable().path)
        val to = File(fileName.path)
        from.renameTo(to)

        facade?.onFinish(download.tag ?: "")

        organizeQueue()
    }

    private fun organizeQueue() {
        with(fetch) {
            getDownloads(Func { list ->
                val working = list.firstOrNull { workingStatuses.contains(it.status) } != null
                val deleted = list.filter { completedStatuses.contains(it.status) }.toTypedArray()
                val downloading = list.filter { pendingStatuses.contains(it.status) }.toTypedArray()

                val shouldDownload = downloading.take(config.concurrentDownloads)
                val shouldPause = downloading.drop(config.concurrentDownloads)

                resume(shouldDownload.asSequence().filter { it.status != DOWNLOADING }.map { it.id }.toList())
                pause(shouldPause.asSequence().filter { it.status != PAUSED }.map { it.id }.toList())
                delete(deleted.map { it.id })

                if (working) notification(downloading.toList(), shouldDownload) else stopSelf()
            })
        }
    }

    private fun getTotalProgress(downloading: List<Download>, shouldDownload: List<Download>): Pair<String, Double> {

        val sum = downloading.sumBy { it.progress }
        val total = downloading.size * 100

        val percent = sum.toDouble() / total

        val names = shouldDownload.asSequence().map { it.extras.getString(DOWNLOAD_NAME_KEY, "") }.filter { it.isNotEmpty() }.joinToString()

        return names to percent
    }

    override fun onDeleted(download: Download) {
        organizeQueue()
    }

    override fun onDownloadBlockUpdated(download: Download, downloadBlock: DownloadBlock, totalBlocks: Int) {}

    override fun onError(download: Download, error: com.tonyodev.fetch2.Error, throwable: Throwable?) {
        facade?.onError(download.tag ?: "")

        organizeQueue()
    }

    override fun onPaused(download: Download) {
        organizeQueue()
    }

    override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
        updateNotification()
        facade?.run {
            onProgress(download.tag ?: "", download.progress)
        }
    }

    override fun onQueued(download: Download, waitingOnNetwork: Boolean) {
        organizeQueue()
    }

    override fun onRemoved(download: Download) {
        organizeQueue()
    }

    override fun onResumed(download: Download) {
        organizeQueue()
    }

    override fun onStarted(download: Download, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) {
        facade?.onStart(download.tag ?: "")

        organizeQueue()
    }

    override fun onWaitingNetwork(download: Download) {}

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
    }

    private val builder by lazy { Notification.Builder(this@DownloadServiceImpl) }
    private fun updateNotification() {
        fetch.getDownloads(Func { list ->
            val downloading = list.filter { pendingStatuses.contains(it.status) }
            val shouldDownload = downloading.take(config.concurrentDownloads)
            notification(downloading, shouldDownload)
        })
    }

    private fun notification(downloading: List<Download>, shouldDownload: List<Download>) {

        val notificationDescription = facade?.notificationSettings ?: return
        val (text, progress) = getTotalProgress(downloading, shouldDownload)

        val goodNetwork = downloading.firstOrNull { networkInfoProvider.isOnAllowedNetwork(it.networkType) } != null
        val isPending = !goodNetwork || shouldDownload.isEmpty()
        val caption = if (isPending) notificationDescription.pendingTitle else notificationDescription.activeTitle

        val notification = builder
            .setSmallIcon(notificationDescription.iconId)
            .setContentTitle(caption)
            .setContentText(text)
            .setProgress(100, (progress * 100).toInt(), isPending)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
