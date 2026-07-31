package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.screens.ApiKeyScreen
import com.example.ui.screens.OutputScreen
import com.example.ui.screens.VideoInputScreen
import com.example.ui.theme.ClipForgeTheme
import com.example.ui.theme.DarkBackground

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ClipForgeTheme {
                val navController = rememberNavController()
                val apiKey by viewModel.apiKey.collectAsState()

                val startDestination = if (apiKey.isNotBlank()) "video_input" else "apikey_setup"

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground)
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("apikey_setup") {
                            ApiKeyScreen(
                                currentApiKey = apiKey,
                                onSaveKey = { newKey ->
                                    viewModel.updateApiKey(newKey)
                                },
                                onContinue = {
                                    navController.navigate("video_input") {
                                        popUpTo("apikey_setup") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("video_input") {
                            VideoInputScreen(
                                viewModel = viewModel,
                                onOpenSettings = {
                                    navController.navigate("apikey_setup")
                                },
                                onGenerationComplete = {
                                    navController.navigate("output")
                                }
                            )
                        }

                        composable("output") {
                            OutputScreen(
                                viewModel = viewModel,
                                onBackToInput = {
                                    navController.navigate("video_input") {
                                        popUpTo("video_input") { inclusive = true }
                                    }
                                },
                                onOpenSettings = {
                                    navController.navigate("apikey_setup")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
