package org.kerlann.easynode

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Autorisations dont l'app a besoin, et ouverture des fenêtres / pages Android pour les accorder.
 * L'app ne peut rien activer elle-même : c'est l'utilisateur qui touche « Autoriser ».
 */
object Permissions {

    /** Notifications (état du nœud, mises à jour). */
    fun notifications(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Batterie « sans restriction » : Android ne doit pas arrêter le nœud. */
    fun battery(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    /** « Installer des applis inconnues » : nécessaire pour les mises à jour depuis l'app. */
    fun install(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Téléphone Samsung : affiche la ligne « Bloqueur automatique ». */
    val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    /** Nombre d'autorisations importantes manquantes (pour le bandeau de l'accueil). */
    fun missing(ctx: Context): Int =
        listOf(notifications(ctx), battery(ctx), install(ctx)).count { !it }

    fun openNotificationSettings(ctx: Context) = open(
        ctx,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
    )

    fun requestBattery(ctx: Context) = open(
        ctx,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
    )

    fun requestInstall(ctx: Context) = open(
        ctx,
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")),
    )

    /** Page « Sécurité et confidentialité » (là où Samsung range le Bloqueur automatique). */
    fun openSecurity(ctx: Context) = open(ctx, Intent(Settings.ACTION_SECURITY_SETTINGS))

    /** Erreur d'installation provoquée par le Bloqueur automatique de Samsung ? */
    fun isAutoBlockerError(message: String?): Boolean =
        message != null && message.contains("blocked by unknown source", ignoreCase = true)

    private fun open(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // Page absente sur ce téléphone : on ouvre les paramètres généraux
            try {
                ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
    }
}
