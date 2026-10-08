package org.example.stocksteps.account

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.stocksteps.MainActivity
import org.example.stocksteps.R
import org.example.stocksteps.data.userdata.PushTokenSource

/**
 * This install's FCM token. Only fetched once notifications are allowed, so the backend has no
 * device to notify while the user hasn't opted in (alerts then show "not sent" in the history).
 */
internal object AndroidPushTokens : PushTokenSource {
    private val mutable = MutableStateFlow<String?>(null)
    override val token: StateFlow<String?> = mutable.asStateFlow()
    override val platform: String = "android"

    fun refresh(context: Context) {
        if (!notificationsAllowed(context) || FirebaseApp.getApps(context).isEmpty()) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { mutable.value = it }
    }

    fun onNewToken(context: Context, token: String) {
        if (notificationsAllowed(context)) mutable.value = token
    }

    fun notificationsAllowed(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

internal object AlertNotifications {
    const val CHANNEL = "stock_alerts"
    const val EXTRA_SYMBOL = "symbol"
    const val EXTRA_TYPE = "type"
    const val EXTRA_BRIEF = "briefId"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.alerts_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.alerts_channel_description)
        })
    }

    /** Opens the app on that stock's alerts (same extras FCM puts on background notifications). */
    fun show(context: Context, title: String, body: String, symbol: String?, type: String = "alert", briefId: String? = null) {
        if (!AndroidPushTokens.notificationsAllowed(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TYPE, type)
            symbol?.let { putExtra(EXTRA_SYMBOL, it) }
            briefId?.let { putExtra(EXTRA_BRIEF, it) }
        }
        val pending = PendingIntent.getActivity(context, (briefId ?: symbol).hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_markets)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try { NotificationManagerCompat.from(context).notify(title.hashCode(), notification) } catch (_: SecurityException) { }
    }
}

/** FCM: token rotation, and showing alerts that arrive while the app is in the foreground. */
class StockStepsMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = AndroidPushTokens.onNewToken(applicationContext, token)

    override fun onMessageReceived(message: RemoteMessage) {
        // Background notification messages are shown by the system; foreground ones arrive here.
        val notification = message.notification ?: return
        AlertNotifications.show(applicationContext, notification.title ?: "StockSteps alert", notification.body.orEmpty(), message.data[AlertNotifications.EXTRA_SYMBOL],
            type = message.data[AlertNotifications.EXTRA_TYPE] ?: "alert", briefId = message.data[AlertNotifications.EXTRA_BRIEF])
    }
}
