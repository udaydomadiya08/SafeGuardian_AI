package com.safeguardian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.safeguardian.app.presentation.navigation.SafeGuardianNavGraph
import com.safeguardian.app.presentation.theme.GuardianDark
import com.safeguardian.app.presentation.theme.SafeGuardianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeGuardianTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = GuardianDark
                ) {
                    val navController = rememberNavController()
                    SafeGuardianNavGraph(navController = navController)
                }
            }
        }
    }
}
