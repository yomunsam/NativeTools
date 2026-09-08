package app.yomu.netbar.netspeed.notification

import android.app.MiuiNotification
import android.app.Notification
import androidx.core.app.NotificationCompat
import app.yomu.netbar.util.field
import app.yomu.netbar.util.getNotnull

/** MIUI */
class MIUINotificationBuilder(private val builder: NotificationCompat.Builder) :
    NotificationExtension.Builder {

    override fun build(): Notification {
        val notification = builder.build()
        try {
            Notification::class
                .java
                .field("extraNotification")
                .getNotnull<MiuiNotification>(notification)
                .setCustomizedIcon(true)
        } catch (e: Throwable) {}
        return notification
    }
}
