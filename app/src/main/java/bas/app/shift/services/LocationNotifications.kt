package bas.app.shift.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import bas.app.shift.MainActivity
import bas.app.shift.R
import bas.app.shift.helpers.ChangeType
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.ProfileChange
import bas.app.shift.helpers.ProfileDiffer
import bas.app.shift.models.FamiliarData
import bas.app.shift.models.Point
import bas.app.shift.ui.ChatsListActivity
import bas.app.shift.ui.MessagesChatActivity
import bas.app.shift.ui.NotificationDetailActivity
import bas.app.shift.ui.ProfileActivity
import bas.app.shift.ui.FamiliarFoundActivity

/**
 * Все уведомления LocationService (канал сервиса, вход/выход из точек, фамильяры, изменения
 * профиля, новые сообщения) в одном месте — вынесено из LocationService, чтобы сервис отвечал
 * только за отслеживание локации и оркестрацию, а не ещё и за вёрстку нотификаций.
 */
class LocationNotifications(private val context: Context) {

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val locationChannel = NotificationChannel(
                CHANNEL_ID,
                "Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Сервис отслеживания геолокации"
                setShowBadge(false)
            }

            val pointsChannel = NotificationChannel(
                POINTS_CHANNEL_ID,
                "Уведомления о точках",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления при входе в игровые точки с важной информацией"
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                setVibrationPattern(longArrayOf(0, 250, 250, 250))
            }

            notificationManager().apply {
                createNotificationChannel(locationChannel)
                createNotificationChannel(pointsChannel)
            }
        }
    }

    fun createMessagesChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MESSAGES_CHANNEL_ID,
                "Сообщения",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления о новых сообщениях"
            }
            notificationManager().createNotificationChannel(channel)
        }
    }

    fun buildServiceNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Shift - Отслеживание геолокации")
            .setContentText("Сервис активен и отслеживает ваше местоположение")
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showPointNotification(title: String, text: String, notificationId: Int) {
        val intent = Intent(context, NotificationDetailActivity::class.java)
        intent.putExtra(NotificationDetailActivity.EXTRA_TITLE, title)
        intent.putExtra(NotificationDetailActivity.EXTRA_TEXT, text)
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(text)
            .setBigContentTitle(title)

        val notification = NotificationCompat.Builder(context, POINTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text.take(80))
            .setStyle(bigTextStyle)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager().notify(notificationId, notification)
        LogHelper.d("LocationNotifications: Показано уведомление для точки $notificationId: $title")
    }

    fun showFamiliarNotification(point: Point) {
        val familiarId = point.description ?: "familiar_malachite_lizard"
        val familiarName = FamiliarData.getNameById(familiarId)

        // Делаем Intent уникальным для сравнения PendingIntent'ов:
        val intent = Intent(context, FamiliarFoundActivity::class.java).apply {
            putExtra("familiar_id", familiarId)
            action = "bas.app.shift.ACTION_OPEN_FAMILIAR.$familiarId"
            data = Uri.parse("shift://familiar/$familiarId")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            familiarId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationText = "Вы чувствуете здесь присутствие $familiarName. Это существо готово к общению. Нажмите на уведомление, чтобы пообщаться с ним!"

        val notification = NotificationCompat.Builder(context, POINTS_CHANNEL_ID)
            .setContentTitle("🐉 Фамильяр рядом!")
            .setContentText("Вы чувствуете здесь $familiarName. Нажмите для общения!")
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText).setBigContentTitle("🐉 Фамильяр рядом!"))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        notificationManager().notify(point.pointId.hashCode(), notification)
        LogHelper.d("LocationNotifications: Показано уведомление о фамильяре для точки ${point.pointId}: $familiarName")
    }

    fun cancelPointNotification(pointId: String) {
        notificationManager().cancel(pointId.hashCode())
    }

    fun showProfileChangeNotifications(changes: List<ProfileChange>) {
        changes.forEachIndexed { index, change ->
            val notificationId = 2000 + index

            val intent = Intent(context, ProfileActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val (title, priority, vibration) = if (change.fieldName == "Эффект") {
                when (change.changeType) {
                    ChangeType.ADDED -> Triple("✨ Новый эффект!", NotificationCompat.PRIORITY_HIGH, longArrayOf(0, 500, 200, 500))
                    ChangeType.REMOVED -> Triple("❌ Эффект исчез", NotificationCompat.PRIORITY_HIGH, longArrayOf(0, 500, 200, 500))
                    else -> Triple("Профиль обновлен", NotificationCompat.PRIORITY_DEFAULT, null)
                }
            } else {
                Triple("Профиль обновлен", NotificationCompat.PRIORITY_DEFAULT, null)
            }

            val notification = NotificationCompat.Builder(context, POINTS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setContentTitle(title)
                .setContentText(ProfileDiffer.formatMessage(change))
                .setPriority(priority)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .apply {
                    if (vibration != null) {
                        setVibrate(vibration)
                    }
                    if (change.fieldName == "Эффект") {
                        setDefaults(NotificationCompat.DEFAULT_ALL)
                    }
                }
                .build()

            try {
                notificationManager().notify(notificationId, notification)
                LogHelper.d("LocationNotifications: Показано уведомление об изменении профиля: ${change.fieldName}")
            } catch (e: Exception) {
                LogHelper.e("LocationNotifications: Ошибка показа уведомления об изменении профиля: ${e.message}")
            }
        }
    }

    fun showMessagesNotification(unreadCount: Int, isMG: Boolean) {
        val message = if (unreadCount == 1) {
            "У вас 1 новое сообщение"
        } else {
            "У вас $unreadCount новых сообщений"
        }

        createMessagesChannel()

        val intent = if (isMG) {
            Intent(context, ChatsListActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        } else {
            Intent(context, MessagesChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MESSAGES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle("Новые сообщения")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager().notify(MESSAGES_NOTIFICATION_ID, notification)
        LogHelper.d("LocationNotifications: Показано уведомление о $unreadCount новых сообщениях (isMG: $isMG)")
    }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "location_service_channel"
        const val POINTS_CHANNEL_ID = "points_notifications_channel"
        const val MESSAGES_CHANNEL_ID = "messages_channel"
        const val NOTIFICATION_ID = 1001
        const val MESSAGES_NOTIFICATION_ID = 1
    }
}
