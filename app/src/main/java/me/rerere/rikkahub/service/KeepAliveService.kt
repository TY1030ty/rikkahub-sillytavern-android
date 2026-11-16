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
        // Android 15 起 dataSync 类型有每日时长上限，超了 startForeground 会抛
        // ForegroundServiceStartNotAllowedException，不接住就是整个应用崩溃。
        val ok = runCatching { startForeground(NOTIFICATION_ID, notification) }.isSuccess
        if (!ok) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 应用一起来就把守护那几个线程挂回去；之后每 10 分钟自检一次，
        // 线程死了（进程还在但线程崩了、或系统回收过）就补上——
        // 之前只挂一次，结果线程一死就再也没人拉，表现是读屏/点评静默好几天。
        Thread {
            while (true) {
                runCatching {
                    if (!com.chaquo.python.Python.isStarted()) {
                        com.chaquo.python.Python.start(
                            com.chaquo.python.android.AndroidPlatform(applicationContext)
                        )
                    }
                    val py = com.chaquo.python.Python.getInstance()
                    py.getModule("builtins").callAttr(
                        "exec",
                        """
                        import sys, threading
                        p = '/sdcard/Download/06_脚本/mirrow'
                        if p not in sys.path:
                            sys.path.insert(0, p)
                        names = [t.name for t in threading.enumerate()]
                        if 'guardian' not in names:
                            import autostart
                            autostart.main()
                        """.trimIndent(),
                    )
                }
                try { Thread.sleep(10 * 60 * 1000L) } catch (_: Exception) { return@Thread }
            }
        }.apply { isDaemon = true }.start()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
