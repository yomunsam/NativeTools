package app.yomu.netbar.netspeed.service

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import app.yomu.netbar.R
import app.yomu.netbar.main.MainActivity
import app.yomu.netbar.netspeed.NetSpeedConfiguration
import app.yomu.netbar.netspeed.notification.NotificationExtension
import app.yomu.netbar.netspeed.utils.NetFormatter
import app.yomu.netbar.netspeed.utils.NetTextIconFactory
import app.yomu.netbar.netusage.utils.NetUsageUtils
import app.yomu.netbar.util.*

/** 网速通知 */
object NetSpeedNotificationHelper {

    const val NOTIFICATION_ID = 10

    private const val CHANNEL_GROUP_ID = "net_speed_channel_group"

    private const val CHANNEL_ID_DEFAULT = "net_speed_channel_default"
    private const val CHANNEL_ID_SILENCE = "net_speed_channel_silence"

    // TEMP_POWER: dirty notification — skip notify when text + icon unchanged
    @Volatile private var lastContentTitle: String? = null
    @Volatile private var lastContentText: String? = null
    @Volatile private var lastSilence: Boolean? = null
    @Volatile private var lastIconBitmap: Bitmap? = null

    private fun isSecure(context: Context): Boolean {
        val keyguardManager = context.requireSystemService<KeyguardManager>()
        return keyguardManager.isDeviceSecure || keyguardManager.isKeyguardSecure
    }

    /** 锁屏通知显示设置 */
    fun goLockHideNotificationSetting(context: Context) {
        val intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isSecure(context)) {
                Intent(
                    Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS,
                    Settings.EXTRA_APP_PACKAGE to context.packageName,
                    Settings.EXTRA_CHANNEL_ID to CHANNEL_ID_DEFAULT
                )
            } else {
                // Settings.ACTION_NOTIFICATION_SETTINGS
                Intent("android.settings.NOTIFICATION_SETTINGS")
            }
        intent.newTask().launchActivity(context)
    }

    /** Open app-level notification settings (for POST_NOTIFICATIONS / master switch). */
    fun goNotificationSetting(context: Context) {
        val packageName = context.packageName
        val intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                    Settings.EXTRA_APP_PACKAGE to packageName
                )
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName")
            }
        intent.newTask().launchActivity(context)
    }

    /**
     * Whether the net-speed FGS notification is allowed to post.
     * On API 33+ also requires [Manifest.permission.POST_NOTIFICATIONS].
     */
    fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun areNotificationEnabled(context: Context): Boolean {
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        val areNotificationsEnabled = notificationManagerCompat.areNotificationsEnabled()
        var channelDisabled = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel =
                notificationManagerCompat.getNotificationChannel(CHANNEL_ID_DEFAULT)
            if (notificationChannel != null) {
                val importance = notificationChannel.importance
                channelDisabled = importance <= NotificationManagerCompat.IMPORTANCE_MIN
            }
        }

        return areNotificationsEnabled && !channelDisabled
    }

    /** 获取数据使用量 — NetworkStatsManager device buckets, no IMSI. */
    private fun getUsageText(context: Context, configuration: NetSpeedConfiguration): String? {
        if (!Logic.checkAppOps(context)) {
            return null
        }

        var todayBytes: Long
        var monthBytes: Long
        val sb = StringBuilder()
        if (!configuration.enableWifiUsage && !configuration.enableMobileUsage) {
            // wifi和移动流量都关闭，显示全部（设备级汇总，subscriberId=null）
            todayBytes =
                NetUsageUtils.getNetUsageBytes(
                    context,
                    NetUsageUtils.TYPE_WIFI,
                    NetUsageUtils.RANGE_TYPE_TODAY
                ) +
                    NetUsageUtils.getNetUsageBytes(
                        context,
                        NetUsageUtils.TYPE_MOBILE,
                        NetUsageUtils.RANGE_TYPE_TODAY
                    )
            monthBytes =
                NetUsageUtils.getNetUsageBytes(
                    context,
                    NetUsageUtils.TYPE_WIFI,
                    NetUsageUtils.RANGE_TYPE_MONTH
                ) +
                    NetUsageUtils.getNetUsageBytes(
                        context,
                        NetUsageUtils.TYPE_MOBILE,
                        NetUsageUtils.RANGE_TYPE_MONTH
                    )
            sb.append(getUsageText(context, todayBytes, monthBytes))
            return sb.toString()
        }

        if (configuration.enableWifiUsage) {
            // wifi 流量
            sb.append("WLAN • ")
            todayBytes =
                NetUsageUtils.getNetUsageBytes(
                    context,
                    NetUsageUtils.TYPE_WIFI,
                    NetUsageUtils.RANGE_TYPE_TODAY
                )
            monthBytes =
                NetUsageUtils.getNetUsageBytes(
                    context,
                    NetUsageUtils.TYPE_WIFI,
                    NetUsageUtils.RANGE_TYPE_MONTH
                )
            sb.append(getUsageText(context, todayBytes, monthBytes))
        }

        if (!configuration.enableMobileUsage) {
            return sb.toString()
        }

        if (configuration.enableWifiUsage) {
            sb.appendLine()
        }
        // 移动流量：设备级汇总，不再按 IMSI 分卡手填
        todayBytes =
            NetUsageUtils.getNetUsageBytes(
                context,
                NetUsageUtils.TYPE_MOBILE,
                NetUsageUtils.RANGE_TYPE_TODAY
            )
        monthBytes =
            NetUsageUtils.getNetUsageBytes(
                context,
                NetUsageUtils.TYPE_MOBILE,
                NetUsageUtils.RANGE_TYPE_MONTH
            )
        sb.append("Mobile • ").append(getUsageText(context, todayBytes, monthBytes))
        return sb.toString()
    }

    private fun getUsageText(context: Context, todayBytes: Long, monthBytes: Long): String {
        return context.getString(
            R.string.notify_net_speed_sub,
            NetFormatter.format(todayBytes, NetFormatter.FLAG_BYTE, NetFormatter.ACCURACY_EXACT)
                .splicing(),
            NetFormatter.format(monthBytes, NetFormatter.FLAG_BYTE, NetFormatter.ACCURACY_EXACT)
                .splicing()
        )
    }

    /** TEMP_POWER: drop cached icon / text so the next notify posts. */
    fun invalidateDirtyCache() {
        val old = lastIconBitmap
        lastIconBitmap = null
        lastContentTitle = null
        lastContentText = null
        lastSilence = null
        BitmapPoolAccessor.recycle(old)
    }

    fun notification(
        context: Context,
        configuration: NetSpeedConfiguration,
        rxSpeed: Long,
        txSpeed: Long,
    ) {
        val silence = configuration.showBlankNotification
        val downloadSpeedStr: String =
            NetFormatter.format(rxSpeed, NetFormatter.FLAG_FULL, NetFormatter.ACCURACY_EXACT)
                .splicing()
        val uploadSpeedStr: String =
            NetFormatter.format(txSpeed, NetFormatter.FLAG_FULL, NetFormatter.ACCURACY_EXACT)
                .splicing()
        val contentTitle =
            context.getString(R.string.notify_net_speed_msg, uploadSpeedStr, downloadSpeedStr)
        val contentText =
            if (configuration.usage) getUsageText(context, configuration) else null

        val iconBitmap =
            if (configuration.showBlankNotification) {
                NetTextIconFactory.createBlank()
            } else {
                NetTextIconFactory.create(rxSpeed, txSpeed, configuration)
            }

        // TEMP_POWER: skip NotificationManager.notify when content unchanged
        val lastBitmap = lastIconBitmap
        if (
            lastSilence == silence &&
                lastContentTitle == contentTitle &&
                lastContentText == contentText &&
                lastBitmap != null &&
                lastBitmap.sameAs(iconBitmap)
        ) {
            BitmapPoolAccessor.recycle(iconBitmap)
            return
        }

        val smileIcon = IconCompat.createWithBitmap(iconBitmap)
        val notification =
            createNotification(
                context,
                configuration,
                smileIcon,
                contentTitle,
                contentText,
            )
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)

        lastContentTitle = contentTitle
        lastContentText = contentText
        lastSilence = silence
        lastIconBitmap = iconBitmap
        if (lastBitmap !== iconBitmap) {
            BitmapPoolAccessor.recycle(lastBitmap)
        }
    }

    fun startForeground(context: Service, configuration: NetSpeedConfiguration) {
        invalidateDirtyCache()
        val iconBitmap =
            if (configuration.showBlankNotification) {
                NetTextIconFactory.createBlank()
            } else {
                NetTextIconFactory.create(0, 0, configuration)
            }
        val smileIcon = IconCompat.createWithBitmap(iconBitmap)
        val downloadSpeedStr: String =
            NetFormatter.format(0, NetFormatter.FLAG_FULL, NetFormatter.ACCURACY_EXACT).splicing()
        val uploadSpeedStr: String =
            NetFormatter.format(0, NetFormatter.FLAG_FULL, NetFormatter.ACCURACY_EXACT).splicing()
        val contentTitle =
            context.getString(R.string.notify_net_speed_msg, uploadSpeedStr, downloadSpeedStr)
        val contentText =
            if (configuration.usage) getUsageText(context, configuration) else null
        val notification =
            createNotification(context, configuration, smileIcon, contentTitle, contentText)
        // API 34+: pass FGS type matching the manifest specialUse declaration.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            context.startForeground(NOTIFICATION_ID, notification)
        }
        // Cache posted content; keep bitmap until next dirty update recycles it
        lastContentTitle = contentTitle
        lastContentText = contentText
        lastSilence = configuration.showBlankNotification
        lastIconBitmap = iconBitmap
    }

    private fun createSmileIcon(
        configuration: NetSpeedConfiguration,
        rxSpeed: Long,
        txSpeed: Long,
    ): IconCompat {
        val bitmap =
            if (configuration.showBlankNotification) {
                NetTextIconFactory.createBlank()
            } else {
                NetTextIconFactory.create(rxSpeed, txSpeed, configuration)
            }
        return IconCompat.createWithBitmap(bitmap)
    }

    /**
     * 创建网速通知
     *
     * @param context 上下文
     * @param configuration 配置
     * @param smileIcon 小图标
     * @param contentTitle 标题（上传/下载速度文案）
     * @param contentText 副标题（流量使用，可空）
     */
    private fun createNotification(
        context: Context,
        configuration: NetSpeedConfiguration,
        smileIcon: IconCompat,
        contentTitle: String,
        contentText: String?,
    ): Notification {
        val silence = configuration.showBlankNotification
        createChannels(context, silence)

        val builder =
            if (silence) {
                // 显示透明图标，并降低通知优先级
                NotificationCompat.Builder(context, CHANNEL_ID_SILENCE)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
            } else {
                NotificationCompat.Builder(context, CHANNEL_ID_DEFAULT)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
            }

        builder
            .setSmallIcon(smileIcon)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setLocalOnly(true)
            .setShowWhen(false)
            .setCategory(null)
            .setSound(null)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setColorized(false)

        if (configuration.hideLockNotification) {
            builder.setVisibility(NotificationCompat.VISIBILITY_SECRET)
        } else {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        var pendingFlag = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // https://developer.android.com/about/versions/12/behavior-changes-all#foreground-service-notification-delay
            builder.foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            // https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability
            // Content/action intents are fully specified; prefer IMMUTABLE over MUTABLE.
            pendingFlag = pendingFlag or PendingIntent.FLAG_IMMUTABLE
        }

        builder.setContentTitle(contentTitle)

        if (configuration.usage) {
            builder.setContentText(contentText)
            // big text
            if (contentText != null && contentText.lines().size > 1) {
                // 多行文字
                val bigTextStyle =
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(contentTitle)
                        .bigText(contentText)
                builder.setStyle(bigTextStyle)
            }
        }

        if (configuration.quickCloseable) {
            val closePending =
                Intent(NetSpeedService.ACTION_CLOSE).toPendingBroadcast(context, pendingFlag)
            builder.addAction(closePending.toNotificationCompatAction(R.string.action_close))
        }

        if (configuration.notifyClickable) {
            // 默认启动应用首页
            val pendingIntent =
                Intent(context, MainActivity::class.java)
                    .newTask()
                    .toPendingActivity(context, pendingFlag)
            builder.setContentIntent(pendingIntent)
        }

        return NotificationExtension.build(builder)
    }

    private fun createChannels(context: Context, silence: Boolean) {
        val manager = NotificationManagerCompat.from(context)

        val channelGroup =
            NotificationChannelGroupCompat.Builder(CHANNEL_GROUP_ID)
                .setName(context.getString(R.string.label_net_speed_service))
                .setDescription(context.getString(R.string.desc_net_speed_notify))
                .build()
        manager.createNotificationChannelGroup(channelGroup)

        val channel = context.createChannel(silence)
        manager.createNotificationChannel(channel)
    }

    private fun Context.createChannel(silence: Boolean): NotificationChannelCompat {
        val builder =
            if (silence) {
                NotificationChannelCompat.Builder(
                        CHANNEL_ID_SILENCE,
                        NotificationManagerCompat.IMPORTANCE_LOW
                    )
                    .setName(this.getString(R.string.label_net_speed_silence_channel))
            } else {
                NotificationChannelCompat.Builder(
                        CHANNEL_ID_DEFAULT,
                        NotificationManagerCompat.IMPORTANCE_MAX
                    )
                    .setName(this.getString(R.string.label_net_speed_default_channel))
            }
        return builder
            .setDescription(this.getString(R.string.desc_net_speed_notify))
            .setShowBadge(false)
            .setGroup(CHANNEL_GROUP_ID)
            .setVibrationEnabled(false)
            .setLightsEnabled(false)
            .setSound(null, null)
            .build()
    }
}
