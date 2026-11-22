package io.github.snifferapp.sniffer.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.snifferapp.sniffer.R
import io.github.snifferapp.sniffer.SnifferApp
import io.github.snifferapp.sniffer.towers.DbStatus
import io.github.snifferapp.sniffer.ui.MainActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the engine alive for ambient mode while the screen is off. */
class SnifferService : LifecycleService() {

    private val engine get() = (application as SnifferApp).engine

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_ambient), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            engine.setAmbient(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            NOTIFICATION_ID, notification(engine.state.value.dbStatus),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        engine.setAmbient(true)
        lifecycleScope.launch {
            engine.state.map { it.dbStatus }.distinctUntilChanged().collect { st ->
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(st))
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        engine.setAmbient(false)
        super.onDestroy()
    }

    private fun notification(status: DbStatus): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, SnifferService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val text = if (status == DbStatus.OK) null else getString(R.string.notif_no_db)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.notif_listening))
            .apply { if (text != null) setContentText(text) }
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "ambient"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "stop"

        fun start(context: Context) = context.startForegroundService(Intent(context, SnifferService::class.java))
        fun stop(context: Context) = context.startService(Intent(context, SnifferService::class.java).setAction(ACTION_STOP))
    }
}
