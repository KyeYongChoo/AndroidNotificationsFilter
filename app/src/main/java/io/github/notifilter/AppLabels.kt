package io.github.notifilter

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/** Resolves package names to the app names users recognise. */
object AppLabels {
    private val cache = ConcurrentHashMap<String, String>()

    /** The app's label, or null if the app isn't visible to us (or not installed). */
    fun get(context: Context, packageName: String): String? {
        cache[packageName]?.let { return it }
        val pm = context.packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                .also { cache[packageName] = it }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** Launchable apps plus [extra] packages, as (label, packageName) sorted by label. */
    fun installedApps(context: Context, extra: Collection<String>): List<Pair<String, String>> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val packages = context.packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .toSet() + extra
        return packages
            .filter { it != context.packageName }
            .map { (get(context, it) ?: it) to it }
            .sortedBy { it.first.lowercase() }
    }
}
