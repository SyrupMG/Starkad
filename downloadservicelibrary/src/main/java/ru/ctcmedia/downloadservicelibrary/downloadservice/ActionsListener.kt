package ru.ctcmedia.downloadservicelibrary.downloadservice

import com.tonyodev.fetch2.Download
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.Downloadable

interface ActionsListener {
    fun cancel(downloadable: Downloadable)
    fun current(callback: (List<Download>?) -> Unit)
    fun reinit()
}