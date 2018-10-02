package ru.ctcmedia.downloadservicelibrary.downloadservice.settings

import android.content.Context
import com.tonyodev.fetch2.NetworkType.ALL
import com.tonyodev.fetch2.NetworkType.WIFI_ONLY
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.NetworkType.Wifi

object Settings {
    var concurrentDownloads: Int = 1
    var networkType: NetworkType = Wifi
    var context: (() -> Context)? = null
}

sealed class NetworkType(val value: com.tonyodev.fetch2.NetworkType) {
    object Wifi : NetworkType(WIFI_ONLY)
    object Cellular : NetworkType(ALL)
}