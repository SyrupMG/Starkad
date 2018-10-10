package ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces

import android.net.Uri
import android.os.Parcelable
import ru.ctcmedia.downloadservicelibrary.downloadservice.DownloadService
import java.io.File

/*
* Расширение добавляющее .downloadable к uri
* */
fun Uri.downloadable(): Uri = Uri.parse(this.toString() + ".downloadable")

/*
* Интерфейс который должны имплементировать классы которые могут быть скачаны
* Позволяет подписываться на объекты данного интерфейса
* Стартовать загрузку
* Отменять загрузку
* Проверять статус загрузки
* */
interface Downloadable : Parcelable {
    /*
    * Уникальный идентификатор объекта
    * */
    val downloadableUniqueId: String

    /*
    * Uri указывающий откуда качать файл
    * */
    val remoteUrl: Uri

    /*
    * Uri указывающий путь куда сохранить файл
    * */
    val localUrl: Uri

    /*
    * Имя для хранения скачиваемого файла
    * */
    val downloadableName: String?
}

/*
* Проверка существования полностью скачанного файла на диске
* */
val Downloadable.isDownloadLocalFileExist: Boolean
    get() = File(localUrl.path).exists()

/*
* Проверка существования временного файла
* */
val Downloadable.isDownloading: Boolean
    get() = File(localUrl.downloadable().path).exists()

/*
* Получение процента скачанного файла
* */
fun Downloadable.getProgress(callback: (Double) -> Unit) {
    DownloadService.progressFor(this, callback)
}

/*
* Метод для осуществления подписки на события объекта
* */
fun Downloadable.observe(listener: DownloadStatusListener) {
    DownloadService.register(listener, this)
}


/*
* Метод для осуществления отписки от событий объекта
* */
fun Downloadable.forget(listener: DownloadStatusListener) {
    DownloadService.unregister(listener, this)
}

/*
* Метод стартует/Продолжает загрузку данного объекта
* */
fun Downloadable.resumeDownload() {
    DownloadService.download(this)
}

/*
* Метод отменяет закачку объекта
* */
fun Downloadable.cancelDownload() {
    DownloadService.cancel(this)
}
