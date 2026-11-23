package com.marco90.mlinfotainment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.marco.mltheme.MLTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MLTheme { Text("MLinfotainment", color = MaterialTheme.colorScheme.onSurface) } }
    }
}
