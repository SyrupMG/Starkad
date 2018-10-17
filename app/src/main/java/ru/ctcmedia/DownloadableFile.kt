package ru.ctcmedia

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable.Creator
import ru.ctcmedia.downloadservicelibrary.downloadservice.interfaces.Downloadable

class DownloadableFile : Downloadable {

    override val downloadableName: String?
        get() = remoteUrl.lastPathSegment

    constructor(remoteUrl: String, localUrl: String) {
        this.remoteUrl = Uri.parse(remoteUrl)
        this.localUrl = Uri.parse(localUrl)
    }

    constructor(parcel: Parcel) {
        remoteUrl = Uri.parse(parcel.readString())
        localUrl = Uri.parse(parcel.readString())
    }

    override val downloadableUniqueId: String
        get() = "$remoteUrl||$localUrl".hashCode().toString()

    override val remoteUrl: Uri
    override val localUrl: Uri

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(remoteUrl.path)
        parcel.writeString(localUrl.path)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Creator<DownloadableFile> {
        override fun createFromParcel(parcel: Parcel): DownloadableFile {
            return DownloadableFile(parcel)
        }

        override fun newArray(size: Int): Array<DownloadableFile?> {
            return arrayOfNulls(size)
        }
    }
}