package ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import ru.ctcmedia.downloadservicelibrary.downloadservice.DownloadService
import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.FileDownloadProgress
import java.io.File

/**
 * Расширение добавляющее .downloadable к uri
 */
fun Uri.downloadable(): Uri = Uri.parse(toString() + ".downloadable")

/**
 * Интерфейс который должны имплементировать классы которые могут быть скачаны
 * Позволяет подписываться на объекты данного интерфейса
 * Стартовать загрузку
 * Отменять загрузку
 * Проверять статус загрузки
 */
interface Downloadable : Parcelable {
    /**
     * Уникальный идентификатор объекта
     */
    val downloadableUniqueId: String

    /**
     * Uri указывающий откуда качать файл
     */
    val remoteUrl: Uri

    /**
     * Uri указывающий путь куда сохранить файл
     */
    val localUrl: Uri

    /**
     * Имя для хранения скачиваемого файла
     */
    val downloadableName: String?
}

/**
 * Проверка существования полностью скачанного файла на диске
 */
val Downloadable.isDownloadLocalFileExist: Boolean
    get() = File(localUrl.path).exists()

/**
 * Проверка существования временного файла
 */
val Downloadable.isDownloading: Boolean
    get() = File(localUrl.downloadable().path).exists()

/**
 * Получение процента скачанного файла
 */
fun Downloadable.getProgress(callback: (FileDownloadProgress) -> Unit) {
    DownloadService.progressFor(this, callback)
}

/**
 * Метод для осуществления подписки на события объекта
 */
infix fun DownloadStatusListener.observe(downloadable: Downloadable?) {
    downloadable ?: return
    DownloadService.register(this, downloadable)
}

/**
 * Метод для осуществления отписки от событий объекта
 */
infix fun DownloadStatusListener.forget(downloadable: Downloadable?) {
    downloadable ?: return
    DownloadService.unregister(this, downloadable)
}

/**
 * Метод стартует/Продолжает загрузку данного объекта
 */
infix fun Context.resume(downloadable: Downloadable?) {
    downloadable ?: return
    with(DownloadService) {
        download(downloadable)
    }
}

/**
 * Метод отменяет закачку объекта
 */
infix fun Context.cancel(downloadable: Downloadable?) {
    downloadable ?: return
    with(DownloadService) {
        cancel(downloadable)
    }
}