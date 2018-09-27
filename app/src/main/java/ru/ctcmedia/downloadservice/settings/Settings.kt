package ru.ctcmedia.downloadservice.settings

import android.content.Context
import com.tonyodev.fetch2.NetworkType.ALL
import com.tonyodev.fetch2.NetworkType.WIFI_ONLY
import ru.ctcmedia.downloadservice.settings.NetworkType.Cellular

object Settings {
    val concurrentDownloads: Int = 1
    val networkType: NetworkType = Cellular
    var context: (() -> Context)? = null
}

sealed class NetworkType(val value: com.tonyodev.fetch2.NetworkType) {
    object Wifi : NetworkType(WIFI_ONLY)
    object Cellular : NetworkType(ALL)
}