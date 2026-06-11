package com.example.callcenter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callcenter.navigation.AppNavGraph
import com.example.callcenter.ui.theme.CallCenterTheme
import com.example.callcenter.ui.theme.ThemeViewModel
import com.example.callcenter.ui.theme.resolveDarkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeOverride by themeViewModel.themeOverride.collectAsState()
            CallCenterTheme(darkTheme = resolveDarkTheme(themeOverride)) {
                AppNavGraph()
            }
        }
    }
}
