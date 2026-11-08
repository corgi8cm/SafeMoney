package io.github.corgi8cm.safemoney.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * App 當掉時把錯誤原因記在手機裡（只存在本機，不會上傳）。
 * 使用者可以在設定頁匯出，方便回報問題。只保留最後 [MAX_BYTES]。
 */
object CrashLog {
    private const val FILE_NAME = "crash-log.txt"
    private const val MAX_BYTES = 64 * 1024

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { append(app, entry(app, thread.name, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() && it.length() > 0 }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    internal fun entry(context: Context, threadName: String, error: Throwable, now: Instant = Instant.now()): String {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("===== $now =====")
            appendLine("App $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: $threadName")
            appendLine(trace)
        }
    }

    internal fun append(context: Context, text: String) {
        val f = file(context)
        val combined = (if (f.exists()) f.readText() else "") + text
        // 超過上限時只留最後面（最新）的內容
        f.writeText(if (combined.length > MAX_BYTES) combined.takeLast(MAX_BYTES) else combined)
    }
}
