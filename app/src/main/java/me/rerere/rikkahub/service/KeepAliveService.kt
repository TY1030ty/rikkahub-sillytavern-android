package me.rerere.rikkahub.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import me.rerere.rikkahub.RouteActivity

/**
 * 常驻前台服务：只为了让系统别把整个进程冻掉。
 *
 * 守护/抓人/记账那些线程跑在应用进程里，进程一被冻结，线程也跟着停。
 * 挂一个低优先级通知常驻，配合电池白名单，锁屏、切后台时进程能保住。
 */
class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "rikkahub_keepalive"
        private const val NOTIFICATION_ID = 2099

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "后台常驻", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, RouteActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("影 正在后台运行")
            .setContentText("守护、记录、记账都在跑")
            .setSmallIcon(applicationInfo.icon)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pi)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
