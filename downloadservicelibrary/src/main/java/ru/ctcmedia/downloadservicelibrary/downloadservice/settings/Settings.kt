package ru.ctcmedia.downloadservicelibrary.downloadservice.settings

import android.os.Parcelable
import com.tonyodev.fetch2.NetworkType.ALL
import com.tonyodev.fetch2.NetworkType.WIFI_ONLY
import kotlinx.android.parcel.Parcelize
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.NetworkType.Cellular
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.NetworkType.Wifi

/**
 * Structure which describe:
 * Number of possible concurrent downloads,
 * The type of network through which you can download.
 */
@Parcelize
data class Settings(
    val concurrentDownloads: Int = 1,
    val networkType: NetworkType = Cellular
) : Parcelable

/**
 * Network type through which loading occurs.
 */
enum class NetworkType {
    /**
     * When this network type selected - files downloading only by Wi-Fi.
     */
    Wifi,
    /**
     * When this network type selected - files downloading by Wi-Fi and Cellular.
     */
    Cellular;

    internal fun fetchNetworkType() = when (this) {
        Wifi -> WIFI_ONLY
        else -> ALL
    }
}

internal fun com.tonyodev.fetch2.NetworkType.settingsNetworkType() = when (this) {
    WIFI_ONLY -> Wifi
    else -> Cellular
}