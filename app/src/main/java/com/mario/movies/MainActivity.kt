package com.mario.movies

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.mario.movies.ui.theme.MOVIESTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MOVIESTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Directly showing FileBrowser to ensure we see the file list
                    FileBrowser(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
