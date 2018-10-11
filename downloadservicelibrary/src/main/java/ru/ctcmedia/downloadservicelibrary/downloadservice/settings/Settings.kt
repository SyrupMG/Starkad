package ru.ctcmedia.downloadservicelibrary.downloadservice.settings

import com.tonyodev.fetch2.NetworkType.ALL
import com.tonyodev.fetch2.NetworkType.WIFI_ONLY
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.NetworkType.Cellular
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.NetworkType.Wifi

/*
* Класс-структура который описывает:
 * Кол-во возможных одновременных закачек
 * Тип сети по которому возможно скачивание
* */
data class Settings(
    val concurrentDownloads: Int = 1,
    val networkType: NetworkType = Cellular
)

/*
* Тип сети:
* Cellular - мобильная и Wi-Fi
* Wifi- только Wi-Fi
* */
enum class NetworkType {
    Wifi,
    Cellular;

    fun fetchNetworkType() = when (this) {
        Wifi -> WIFI_ONLY
        else -> ALL
    }
}

fun com.tonyodev.fetch2.NetworkType.settingsNetworkType() = when (this) {
    WIFI_ONLY -> Wifi
    else -> Cellular
}