package com.guillaume.vinyl

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class PlayerActivity : ComponentActivity() {

    private val hasAccess = mutableStateOf(false)
    private val canFullScreen = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        setContent {
            val state by Player.state.collectAsState()
            when {
                !hasAccess.value -> Setup(
                    "Pour voir ce que joue Spotify, autorise « Vinyle » dans l'accès aux notifications.",
                    "Autoriser"
                ) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

                !canFullScreen.value -> Setup(
                    "Pour s'afficher sur l'écran de verrouillage, autorise « Vinyle » à afficher des notifications plein écran.",
                    "Autoriser"
                ) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                    )
                }

                else -> VinylScreen(state) { Player.togglePlayPause() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccess.value = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        canFullScreen.value = Build.VERSION.SDK_INT < 34 ||
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        // The full-screen notification did its job once we are visible.
        getSystemService(NotificationManager::class.java).cancel(PLAYER_NOTIF_ID)
        // Ask Android to (re)bind the listener in case it was killed.
        if (hasAccess.value) {
            android.service.notification.NotificationListenerService.requestRebind(
                ComponentName(this, MediaListener::class.java)
            )
        }
    }
}

@Composable
private fun Setup(text: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B0B10)).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
        Button(onClick = onClick) { Text(button) }
    }
}
