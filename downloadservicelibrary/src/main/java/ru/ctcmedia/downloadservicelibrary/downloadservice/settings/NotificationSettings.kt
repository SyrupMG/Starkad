package ru.ctcmedia.downloadservicelibrary.downloadservice.settings

import android.app.Notification
import com.tonyodev.fetch2.Download

data class NotificationSettings(
    val foregroundNotification: Notification.Builder,
    val downloadingNotificationBuilder: (Download) -> Notification.Builder,
    val downloadingErrorNotification: Notification.Builder,
    val downloadingCompleteNotification: Notification.Builder
)