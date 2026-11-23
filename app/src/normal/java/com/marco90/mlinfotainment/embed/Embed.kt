package com.marco90.mlinfotainment.embed

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Versione normale: il navigatore non si può incorporare (serve la versione "Sistema"). */
object EmbedSupport {
    fun init(ctx: Context) {}
}

@Composable
fun EmbeddedNav(modifier: Modifier, pkg: String, onClose: () -> Unit) {}

@Composable
fun EmbedProbePage(onBack: () -> Unit) {}
