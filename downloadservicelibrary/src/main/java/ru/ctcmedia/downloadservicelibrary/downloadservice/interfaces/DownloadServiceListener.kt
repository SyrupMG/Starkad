package ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces

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
    fun downloadProgressUpdate(progress: Double) {}

    /*
    * Метод вызывается при окончании скачивания файла
    * */
    fun downloadFinished() {}

    /*
    * Метод вызывается при ошибке скачивания файла
    * */
    fun downloadFailed(error: Error) {}
}