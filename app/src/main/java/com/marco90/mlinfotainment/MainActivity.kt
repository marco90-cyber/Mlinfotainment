package com.marco90.mlinfotainment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.marco.mltheme.MLTheme
import com.marco90.mlinfotainment.core.AppState
import com.marco90.mlinfotainment.core.Triggers
import com.marco90.mlinfotainment.embed.EmbedSupport
import com.marco90.mlinfotainment.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MLTheme { AppRoot() } }
        Triggers.syncAll(this)
        EmbedSupport.init(this)
    }

    override fun onStart() {
        super.onStart()
        AppState.visible = true
    }

    override fun onStop() {
        AppState.visible = false
        super.onStop()
    }
}
