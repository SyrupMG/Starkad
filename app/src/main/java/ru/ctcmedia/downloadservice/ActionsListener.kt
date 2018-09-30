package ru.ctcmedia.downloadservice

import com.tonyodev.fetch2.Download
import ru.ctcmedia.downloadservice.interfaces.Downloadable

interface ActionsListener {
    fun downloadNext()
    fun cancel(downloadable: Downloadable)
    fun current(): Download?
}