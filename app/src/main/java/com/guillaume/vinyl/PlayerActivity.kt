package com.guillaume.vinyl

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

/** Setup screen: grant notification access, then pick the live wallpaper. */
class PlayerActivity : ComponentActivity() {

    private val hasAccess = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Column(
                Modifier.fillMaxSize().background(Color(0xFF0B0B10)).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Vinyle", color = Color.White, fontSize = 28.sp)
                Text(
                    if (hasAccess.value) "1. Accès aux notifications : OK ✓"
                    else "1. Autorise « Vinyle » dans l'accès aux notifications, pour voir ce que joue Spotify.",
                    color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center,
                )
                if (!hasAccess.value) Button(onClick = {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Autoriser") }
                Text(
                    "2. Mets « Vinyle » en fond d'écran. Choisis « Écran de verrouillage » (ou les deux).",
                    color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center,
                )
                Button(onClick = {
                    startActivity(
                        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            ComponentName(this@PlayerActivity, VinylWallpaper::class.java),
                        )
                    )
                }) { Text("Choisir le fond d'écran") }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccess.value = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        // Ask Android to (re)bind the listener in case it was killed.
        if (hasAccess.value) {
            NotificationListenerService.requestRebind(ComponentName(this, MediaListener::class.java))
        }
    }
}
