package com.hamraheteyf.app.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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

enum class HashVerdict { OK, MISSING, MISMATCH }

/** Pure install-gate decisions (unit-tested). */
object UpdateRules {
    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /** A blank/malformed expected hash is a refusal, never a pass. */
    fun hashVerdict(expected: String, actual: String): HashVerdict {
        val e = expected.trim().lowercase()
        if (!HEX64.matches(e)) return HashVerdict.MISSING
        return if (e == actual.trim().lowercase()) HashVerdict.OK else HashVerdict.MISMATCH
    }

    fun isSecureUrl(url: String) = url.startsWith("https://")

    /** Same package, and (when both cert sets are known) at least one shared signing cert. */
    fun sameApp(installedPkg: String, apkPkg: String?, installedCerts: Set<String>, apkCerts: Set<String>): Boolean {
        if (apkPkg != installedPkg) return false
        if (installedCerts.isEmpty() || apkCerts.isEmpty()) return true
        return installedCerts.any { it in apkCerts }
    }
}

object AppUpdater {
    const val MANIFEST_URL = "https://tizbin.org/hamrah/latest.json"

    /** Returns the newer release, or null when up to date. Throws on network errors. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val c = URL("$MANIFEST_URL?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        val j = try { JSONObject(c.inputStream.bufferedReader().use { it.readText() }) } finally { c.disconnect() }
        val info = UpdateInfo(j.getInt("versionCode"), j.optString("versionName"), j.getString("url"), j.optString("notes"), j.optString("sha256").lowercase())
        if (info.versionCode > BuildConfig.VERSION_CODE) info else null
    }

    /** Downloads the APK into the app cache, checks its hash, and returns the file. */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "hamrah-${info.versionCode}.apk")
        if (UpdateRules.hashVerdict(info.sha256, info.sha256) == HashVerdict.MISSING) throw IllegalStateException("اطلاعات به‌روزرسانی ناقص است؛ بعداً دوباره تلاش کنید.")
        if (!UpdateRules.isSecureUrl(info.url)) throw IllegalStateException("نشانی به‌روزرسانی امن نیست.")
        val c = URL(info.url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("دریافت به‌روزرسانی ناموفق بود.")
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
            if (UpdateRules.hashVerdict(info.sha256, hex) != HashVerdict.OK) { out.delete(); throw IllegalStateException("فایل دانلودشده سالم نیست؛ دوباره تلاش کنید.") }
            if (!sameApp(ctx, out)) { out.delete(); throw IllegalStateException("فایل دانلودشده برای این برنامه نیست.") }
        } finally { c.disconnect() }
        out
    }

    @Suppress("DEPRECATION")
    private fun sameApp(ctx: Context, apk: File): Boolean = runCatching {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return@runCatching true
        val installed = pm.getPackageInfo(ctx.packageName, flags)
        UpdateRules.sameApp(ctx.packageName, archive.packageName, certs(installed), certs(archive))
    }.getOrDefault(true) // unreadable archive: the SHA-256 gate already passed, and the OS installer re-checks signatures

    @Suppress("DEPRECATION")
    private fun certs(p: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures
        return sigs.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
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
