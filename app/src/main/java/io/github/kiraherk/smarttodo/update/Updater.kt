package io.github.kiraherk.smarttodo.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import io.github.kiraherk.smarttodo.BuildConfig
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.backup.BackupManager
import io.github.kiraherk.smarttodo.reminders.Notifications
import io.github.kiraherk.smarttodo.util.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@Serializable
data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

@Serializable
data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val pageUrl: String, val size: Long)

object Versions {
    fun parse(version: String): List<Int> = version.trim().removePrefix("v").removePrefix("V")
        .substringBefore('-').split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        .let { it + List((3 - it.size).coerceAtLeast(0)) { 0 } }

    fun isNewer(remote: String, local: String): Boolean {
        val r = parse(remote)
        val l = parse(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}

/** Looks at the latest GitHub release of this repo. Works for a public repo without any token. */
class UpdateChecker(private val http: OkHttpClient, private val repo: String = BuildConfig.GITHUB_REPO) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): GhRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SmartToDo-Android")
            .build()
        http.newCall(request).execute().use { r ->
            if (r.code == 404) throw IOException("No release published yet")
            if (!r.isSuccessful) throw IOException("GitHub HTTP ${r.code}")
            json.decodeFromString(GhRelease.serializer(), r.body.string())
        }
    }

    /** Returns the update if the latest release is newer than this app, else null. */
    suspend fun check(currentVersion: String = BuildConfig.VERSION_NAME): UpdateInfo? {
        val release = latest()
        if (release.draft || release.prerelease) return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        if (!Versions.isNewer(release.tag, currentVersion)) return null
        return UpdateInfo(release.tag.removePrefix("v"), release.body.orEmpty(), apk.url, release.htmlUrl, apk.size)
    }
}

/**
 * Downloads the new APK, checks it is signed with the SAME key as the installed app (so nobody
 * else's APK can replace it), makes a backup, then hands it to Android's installer. Android keeps
 * all app data when an update with the same signature is installed.
 */
class UpdateInstaller(
    private val context: Context,
    private val http: OkHttpClient,
    private val backup: BackupManager,
) {
    sealed class Problem(message: String) : Exception(message) {
        class DebugBuild : Problem("debug build")
        class WrongSignature : Problem("signature mismatch")
        class Download(cause: Throwable) : Problem(cause.message ?: "download failed")
        class NotNewer : Problem("not newer")
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettings(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "SmartToDo-${info.version}.apk")
        try {
            http.newCall(Request.Builder().url(info.apkUrl).header("User-Agent", "SmartToDo-Android").build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val total = r.body.contentLength().takeIf { it > 0 } ?: info.size
                r.body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buf).also { read = it } >= 0) {
                            out.write(buf, 0, read)
                            done += read
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            throw Problem.Download(e)
        }
        file
    }

    /** True if [apk] is this app, newer, and signed by the same key. */
    fun verify(apk: File): Boolean {
        if (BuildConfig.DEBUG) throw Problem.DebugBuild()
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        if (archive.packageName != context.packageName) return false
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (versionCodeOf(archive) <= versionCodeOf(installed)) throw Problem.NotNewer()
        val a = signerDigests(archive)
        val b = signerDigests(installed)
        if (a.isEmpty() || b.isEmpty() || a.intersect(b).isEmpty()) throw Problem.WrongSignature()
        return true
    }

    suspend fun install(apk: File) {
        backup.backupNow("before-update")
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            // An earlier attempt may still be waiting (for example after Play Protect's scan):
            // close it so it can't get in the way of this one.
            installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
            PendingInstall.clear(context)
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setInstallReason(PackageManager.INSTALL_REASON_USER)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            }
            val sessionId = installer.createSession(params)
            PendingInstall.sessionId = sessionId
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val callback = PendingIntent.getBroadcast(context, sessionId, Intent(context, InstallResultReceiver::class.java), flags)
                session.commit(callback.intentSender)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val s = info.signingInfo ?: return emptySet()
            if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        val md = MessageDigest.getInstance("SHA-256")
        return signatures.orEmpty().map { sig -> md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }
}

/**
 * The "Install?" confirmation Android needs him to accept. Android does not let an app open it
 * while another screen is on top (for example Play Protect's "scan app" dialog), so it is kept
 * here and opened as soon as the app is back in front, and offered as a notification as well.
 */
object PendingInstall {
    @Volatile var sessionId: Int = -1
    @Volatile private var confirm: Intent? = null

    /** True while the app's screen is in front (set by MainActivity). */
    @Volatile var appInFront: Boolean = false

    fun hold(context: Context, intent: Intent) {
        confirm = intent
        val c = LocaleHelper.wrap(context)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val tap = PendingIntent.getActivity(context, 7, Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        Notifications.post(
            c, Notifications.ID_INSTALL,
            Notifications.builder(c, Notifications.CH_UPDATES, c.getString(R.string.update_confirm_title), c.getString(R.string.update_confirm_text))
                .setContentIntent(tap)
                .setAutoCancel(true),
        )
    }

    /** Called when the app comes to the front: shows a waiting confirmation, if any. */
    fun showIfWaiting(activity: android.app.Activity) {
        val intent = confirm ?: return
        confirm = null
        NotificationManagerCompat.from(activity).cancel(Notifications.ID_INSTALL)
        runCatching { activity.startActivity(intent) }
    }

    fun clear(context: Context) {
        confirm = null
        NotificationManagerCompat.from(context).cancel(Notifications.ID_INSTALL)
    }
}

/** Android tells us here whether it needs him to confirm the install, or whether it failed. */
class InstallResultReceiver : BroadcastReceiver() {
    @SuppressLint("UnsafeIntentLaunch")
    override fun onReceive(context: Context, intent: Intent) {
        val session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        // Results for an earlier, abandoned attempt don't matter any more.
        if (PendingInstall.sessionId != -1 && session != -1 && session != PendingInstall.sessionId) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                if (PendingInstall.appInFront) {
                    val opened = runCatching { context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                    if (!opened) PendingInstall.hold(context, confirm)
                } else {
                    PendingInstall.hold(context, confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> PendingInstall.clear(context)
            else -> {
                PendingInstall.clear(context)
                val c = LocaleHelper.wrap(context)
                val text = c.getString(
                    when (status) {
                        PackageInstaller.STATUS_FAILURE_ABORTED -> R.string.update_stopped
                        PackageInstaller.STATUS_FAILURE_STORAGE -> R.string.update_no_space
                        PackageInstaller.STATUS_FAILURE_BLOCKED -> R.string.update_blocked
                        else -> R.string.update_try_again
                    }
                )
                Notifications.post(
                    c, Notifications.ID_INSTALL,
                    Notifications.builder(c, Notifications.CH_UPDATES, c.getString(R.string.update_failed), text),
                )
            }
        }
    }
}
