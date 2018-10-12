package ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces

import ru.ctcmedia.downloadservicelibrary.downloadservice.settings.FileDownloadProgress

/*
* Интерфейс описывающий события которые могут порождать классы наследующие Downloadable
* */
interface DownloadStatusListener {
    /*
    * Метод вызывается когда объект начал скачиваться
    * */
    fun downloadBegan() {}

    /*
    * Метод вызывается при изменении процента скачанного файла
    * */

    // TODO(Сделать структуру)
    fun downloadProgressUpdate(progress: FileDownloadProgress) {}

    /*
    * Метод вызывается при окончании скачивания файла
    * */
    fun downloadFinished() {}

    /*
    * Метод вызывается при ошибке скачивания файла
    * */
    fun downloadFailed(error: Error) {}
}