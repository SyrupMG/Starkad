package ru.ctcmedia

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable.Creator
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.Downloadable

class DownloadableFile(remoteUrl: String, localUrl: String) : Downloadable {

    override val downloadableName: String?
        get() = remoteUrl.lastPathSegment

    override val downloadableUniqueId: String
        get() = "$remoteUrl||$localUrl".hashCode().toString()

    override val remoteUrl: Uri = Uri.parse(remoteUrl)
    override val localUrl: Uri = Uri.parse(localUrl)
}