package com.hamraheteyf.app.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.hamraheteyf.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(val versionCode: Int, val versionName: String, val url: String, val notes: String, val sha256: String)

object AppUpdater {
    const val MANIFEST_URL = "https://tizbin.org/hamrah/latest.json"

    /** Returns the newer release, or null when up to date. Throws on network errors. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val c = URL("$MANIFEST_URL?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val info = UpdateInfo(j.getInt("versionCode"), j.optString("versionName"), j.getString("url"), j.optString("notes"), j.optString("sha256").lowercase())
        if (info.versionCode > BuildConfig.VERSION_CODE) info else null
    }

    /** Downloads the APK into the app cache, checks its hash, and returns the file. */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "hamrah-${info.versionCode}.apk")
        val c = URL(info.url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        val total = c.contentLengthLong
        val md = MessageDigest.getInstance("SHA-256")
        c.inputStream.use { inp -> out.outputStream().use { os ->
            val buf = ByteArray(64 * 1024); var done = 0L; var n: Int
            while (inp.read(buf).also { n = it } > 0) {
                os.write(buf, 0, n); md.update(buf, 0, n); done += n
                if (total > 0) onProgress((done * 100 / total).toInt())
            }
        } }
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        if (info.sha256.isNotBlank() && hex != info.sha256) { out.delete(); throw IllegalStateException("فایل دانلودشده سالم نیست؛ دوباره تلاش کنید.") }
        out
    }

    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
