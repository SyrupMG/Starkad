package ru.ctcmedia.downloadservice

import android.app.IntentService
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
import java.io.File

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

    override fun onHandleIntent(intent: Intent) {
        val fetchConfig = FetchConfiguration.Builder(this).setGlobalNetworkType(Settings.networkType.value).build()
        val fetch = Fetch.getInstance(fetchConfig)
        fetch.addListener(this)

        val downloadable = intent.getParcelableExtra<Downloadable>(DOWNLOADABLE_TAG)
        val fileName = Uri.parse(downloadable.remoteUrl).lastPathSegment
        val request = Request(downloadable.remoteUrl, "${this.filesDir}${File.pathSeparator}$fileName")
        fetch.enqueue(request, null, null)
    }

    override fun onAdded(download: Download) {
    }

    override fun onCancelled(download: Download) {
    }

    override fun onCompleted(download: Download) {
        Broadcaster.notify<DownloadServiceListener> { onFinish(download.id.toString()) }
    }

    override fun onDeleted(download: Download) {
    }

    override fun onDownloadBlockUpdated(download: Download, downloadBlock: DownloadBlock, totalBlocks: Int) {
    }

    override fun onError(download: Download, error: Error, throwable: Throwable?) {
        Broadcaster.notify<DownloadServiceListener> { onError(download.id.toString()) }
    }

    override fun onPaused(download: Download) {
        Broadcaster.notify<DownloadServiceListener> { onPause(download.id.toString()) }
    }

    override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
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
}
