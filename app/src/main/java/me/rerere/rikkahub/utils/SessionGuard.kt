package me.rerere.rikkahub.utils

import android.content.Context
import java.io.File

/**
 * SessionGuard —— 崩溃续接
 *
 * 原生崩溃（sqlite 的 SIGBUS、SIGSEGV 之类）不会被 UncaughtExceptionHandler 接住，
 * CrashHandler 的 crashed 标记永远亮不起来，安全模式也就形同不存在。
 *
 * 所以这里换一套判断：前台时把标记文件写成 open，退到后台写成 closed。
 * 下次启动读到 open，就说明上一次没干净结束（崩了，或者被系统直接杀掉），
 * 于是直接回到那个对话，而不是丢给她一个空白的新聊天框。
 *
 * 文件内容两行：第一行 open/closed，第二行最后停留的对话 id。
 */
object SessionGuard {
    private const val FILE_NAME = "session_guard.txt"

    data class State(val dirty: Boolean, val conversationId: String?)

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    fun read(context: Context): State = try {
        val f = file(context)
        if (!f.exists()) {
            State(false, null)
        } else {
            val lines = f.readLines()
            val flag = lines.getOrNull(0)?.trim().orEmpty()
            val convId = lines.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            State(flag != "closed", convId)
        }
    } catch (e: Throwable) {
        State(false, null)
    }

    /** 回到前台 / 切换对话时调用；conversationId 传 null 表示沿用上一次记录的对话 */
    fun markForeground(context: Context, conversationId: String? = null) {
        val keep = conversationId ?: read(context).conversationId
        write(context, "open", keep)
    }

    /** 退到后台视为干净状态：正常离开 App 不会在下次启动时被强行拉回旧对话 */
    fun markBackground(context: Context) {
        write(context, "closed", read(context).conversationId)
    }

    private fun write(context: Context, flag: String, conversationId: String?) {
        try {
            file(context).writeText(flag + "\n" + (conversationId ?: ""))
        } catch (e: Throwable) {
            // 标记文件写不进去就算了，不能因为这个再崩一次
        }
    }
}
