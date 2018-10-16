@file: SuppressLint("SetTextI18n")

package ru.ctcmedia

import android.R.drawable
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_downloadable.view.*
import ru.ctcmedia.FilesAdapter.ViewHolder
import ru.ctcmedia.downloadservice.R
import ru.ctcmedia.downloadservicelibrary.downloadservice.DownloadNotification
import ru.ctcmedia.downloadservicelibrary.downloadservice.DownloadService
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.DownloadStatusListener
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.Downloadable
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.cancel
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.forget
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.getProgress
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.isDownloadLocalFileExist
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.isDownloading
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.observe
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.resume
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.FileDownloadProgress
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.NetworkType.Cellular
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.NetworkType.Wifi
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.NetworkType.valueOf
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.Settings

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val filesLinks = arrayOf(
            DownloadableFile("http://mirror.filearena.net/pub/speed/SpeedTest_16MB.dat", "${filesDir.path}/video/16mb.dat"),
            DownloadableFile("http://mirror.filearena.net/pub/speed/SpeedTest_32MB.dat", "${filesDir.path}/video/32mb.dat"),
            DownloadableFile("http://mirror.filearena.net/pub/speed/SpeedTest_64MB.dat", "${filesDir.path}/video/64mb.dat"),
            DownloadableFile("http://mirror.filearena.net/pub/speed/SpeedTest_128MB.dat", "${filesDir.path}/video/128mb.dat"),
            DownloadableFile("http://mirror.filearena.net/pub/speed/SpeedTest_256MB.dat", "${filesDir.path}/video/256mb.dat"),
            DownloadableFile("http://mirror.filearena.net/pub/speed/SpeedTest_512MB.dat", "${filesDir.path}/video/512mb.dat"),
            DownloadableFile("http://mirror.filearena.net/pub/speed/SpeedTest_1024MB.dat", "${filesDir.path}/video/1024mb.dat")
        )

        val app = this.applicationContext

        with(DownloadService) {

            configuration = loadSettings()

            notifyWith(DownloadNotification(drawable.ic_popup_sync, "Идет загрузка...", "Ожидание сети..."))

            onReady {
                val filesAdapter = FilesAdapter(filesLinks) {
                    when {
                        isDownloading -> app cancel this
                        !isDownloadLocalFileExist -> app resume this
                    }
                }

                recyclerView.apply {
                    adapter = filesAdapter
                    with(LinearLayoutManager(this@MainActivity)) {
                        layoutManager = this
                        addItemDecoration(DividerItemDecoration(this@MainActivity, orientation))
                    }
                }

                spinner.onItemSelectedListener = object : OnItemSelectedListener {
                    override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        configuration = configuration.copy(concurrentDownloads = position + 1).also { saveSettings(it) }
                    }

                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }

                radioGroup.setOnCheckedChangeListener { radioGroup, _ ->
                    configuration = when (radioGroup.checkedRadioButtonId) {
                        R.id.cellularButton -> configuration.copy(networkType = Cellular)
                        R.id.wifiButton -> configuration.copy(networkType = Wifi)
                        else -> throw Exception()
                    }.also { saveSettings(it) }
                }

                spinner.setSelection(configuration.concurrentDownloads - 1)

                when (configuration.networkType) {
                    Cellular -> radioGroup.check(R.id.cellularButton)
                    Wifi -> radioGroup.check(R.id.wifiButton)
                }
            }

            bindContext()
        }
    }

    private val sharedSettings by lazy { getSharedPreferences(packageName, Context.MODE_PRIVATE) }

    private fun loadSettings(): Settings {
        return with(sharedSettings) {
            Settings(getInt("limit", 1), valueOf(getString("mode", Cellular.name) ?: Cellular.name))
        }
    }

    private fun saveSettings(settings: Settings) {
        with(sharedSettings.edit()) {
            putInt("limit", settings.concurrentDownloads)
            putString("mode", settings.networkType.name)
            apply()
        }
    }

    override fun onDestroy() {
        with(DownloadService) { unbindContext() }
        super.onDestroy()
    }
}

class FilesAdapter(private val files: Array<DownloadableFile>, private val click: Downloadable.() -> Unit) : RecyclerView.Adapter<ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, position: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_downloadable, parent, false))

    override fun getItemCount() = files.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(files[position], click)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), DownloadStatusListener {
        var file: DownloadableFile? = null
        fun bind(file: DownloadableFile, click: (Downloadable) -> Unit) {
            this forget this.file

            val status = when {
                file.isDownloading -> "cancelled"
                file.isDownloadLocalFileExist -> "downloaded"
                else -> ""
            }

            itemView.name.text = "${file.downloadableName} $status"
            downloadProgressUpdate(FileDownloadProgress(0.0/**/))
            file.getProgress { downloadProgressUpdate(it) }

            this.file = file
            this observe file

            itemView.setOnClickListener { click(file) }
        }

        override fun downloadBegan() {
            itemView.name.text = "${file?.downloadableName} begin"
        }

        override fun downloadProgressUpdate(progress: FileDownloadProgress) {
            itemView.progressBar.progress = (progress.downloadProgress * 100).toInt()
        }

        override fun downloadFinished() {
            super.downloadFinished()
            itemView.name.text = "${file?.downloadableName} cancelled"
        }

        override fun downloadFailed(error: Error) {
            super.downloadFailed(error)
            itemView.name.text = "${file?.downloadableName} ${error.localizedMessage}"
        }
    }
}