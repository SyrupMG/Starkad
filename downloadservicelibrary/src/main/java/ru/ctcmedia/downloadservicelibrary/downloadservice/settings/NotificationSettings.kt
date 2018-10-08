package ru.ctcmedia.downloadservicelibrary.downloadservice.settings

import android.app.Notification

data class NotificationSettings(
    val foregroundNotification: Notification.Builder,
    val downloadingNotificationBuilder: (builder: Notification.Builder, progress: Int) -> Notification,
    val downloadingErrorNotification: Notification.Builder,
    val downloadingCompleteNotification: Notification.Builder
)