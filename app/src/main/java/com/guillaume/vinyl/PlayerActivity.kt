package com.guillaume.vinyl

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay

/** Setup screen: each thing the lock screen player needs, its live status, and a button to fix it. */
class PlayerActivity : ComponentActivity() {

    private val refresh = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        setContent {
            // Re-read statuses every second (Spotify starting, settings changed…).
            LaunchedEffect(Unit) { while (true) { delay(1000); refresh.intValue++ } }
            refresh.intValue
            val events by Player.events.collectAsState()
            val session = Player.state.collectAsState().value.hasSession
            Column(
                Modifier.fillMaxSize().background(Color(0xFF0B0B10)).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Vinyle — réglages", color = Color.White, fontSize = 26.sp)
                Text("Tout doit être vert ✓ pour que le lecteur apparaisse sur l'écran de verrouillage.",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)

                Step("Accès aux notifications (lire Spotify)", hasListener(), "Autoriser") {
                    open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                Step("Spotify détecté (lance une musique)", session, null) {}
                Step("Notifications autorisées", NotificationManagerCompat.from(this@PlayerActivity).areNotificationsEnabled(), "Autoriser") {
                    open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }
                Step("Afficher par-dessus les autres applis", Settings.canDrawOverlays(this@PlayerActivity), "Autoriser") {
                    open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                Step("Notifications plein écran", canFullScreen(), "Autoriser") {
                    open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
                }
                Step("Batterie non restreinte", noBatteryLimit(), "Autoriser") { askBattery() }
                Step("Fond d'écran Vinyle actif (mets-le sur les 2 écrans)", wallpaperSet(), "Choisir") {
                    open(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@PlayerActivity, VinylWallpaper::class.java)))
                }

                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    Player.log("test demandé : verrouille le téléphone")
                    Handler(Looper.getMainLooper()).postDelayed({ launchLockPlayer(this@PlayerActivity, "test") }, 5000)
                }, modifier = Modifier.fillMaxWidth()) { Text("Tester : appuie puis verrouille le téléphone (5 s)") }

                Text("Derniers événements", color = Color.White, fontSize = 16.sp)
                if (events.isEmpty()) Text("(rien pour l'instant)", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                events.forEach { Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh.intValue++
        // Ask Android to (re)bind the listener in case it was killed or the app was updated.
        if (hasListener()) NotificationListenerService.requestRebind(ComponentName(this, MediaListener::class.java))
    }

    private fun open(i: Intent) {
        runCatching { startActivity(i) }.onFailure { Player.log("écran de réglage introuvable sur ce téléphone") }
    }

    private fun hasListener() = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)

    private fun canFullScreen() = Build.VERSION.SDK_INT < 34 ||
        getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun noBatteryLimit() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    @SuppressLint("BatteryLife") // sideloaded personal app, not on the Play Store
    private fun askBattery() = open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))

    private fun wallpaperSet() = WallpaperManager.getInstance(this).wallpaperInfo?.packageName == packageName
}

@Composable
private fun Step(label: String, ok: Boolean, button: String?, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✓" else "✗", color = if (ok) Color(0xFF1DB954) else Color(0xFFE8417A), fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (!ok && button != null) Button(onClick = onFix) { Text(button) }
    }
}
