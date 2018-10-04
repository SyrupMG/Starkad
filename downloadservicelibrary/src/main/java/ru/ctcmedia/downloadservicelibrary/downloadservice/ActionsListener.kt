package ru.ctcmedia.downloadservicelibrary.downloadservice

import com.tonyodev.fetch2.Download
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.Downloadable
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.Settings

interface ActionsListener {
    fun resume(downloadable: Downloadable)
    fun cancel(downloadable: Downloadable)
    fun current(callback: (List<Download>?) -> Unit)
    fun setSettings(settings: Settings)
    fun getSettings() : Settings
}