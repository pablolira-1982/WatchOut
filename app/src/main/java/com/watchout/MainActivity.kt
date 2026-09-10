package com.watchout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import com.watchout.feature.assistant.AssistantScreen
import com.watchout.core.model.AssistanceMode
import com.watchout.feature.home.AccessibleHomeScreen
import com.watchout.feature.onboarding.OnboardingScreen
import com.watchout.ui.theme.WatchOutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WatchOutTheme {
                val preferences = getSharedPreferences("watchout_preferences", MODE_PRIVATE)
                var modelPath by rememberSaveable {
                    mutableStateOf(preferences.getString("model_path", null))
                }
                var showCamera by rememberSaveable { mutableStateOf(false) }
                var selectedMode by rememberSaveable { mutableStateOf(AssistanceMode.SAFETY) }
                var showInitialGuide by rememberSaveable {
                    mutableStateOf(!preferences.getBoolean("initial_guide_completed", false))
                }
                var showSettings by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(modelPath) {
                    val path = modelPath
                    if (path != null && !File(path).isFile) {
                        preferences.edit().remove("model_path").apply()
                        modelPath = null
                        showCamera = false
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (modelPath != null && showCamera) {
                        AssistantScreen(
                            modelPath = requireNotNull(modelPath),
                            initialMode = selectedMode,
                            onBack = { showCamera = false },
                            onExit = { finish() },
                            onOpenSettings = { showSettings = true },
                            showSettings = showSettings,
                            onCloseSettings = { showSettings = false },
                            onChangeModel = {
                                showSettings = false
                                showCamera = false
                            }
                        )
                    } else if (modelPath != null) {
                        AccessibleHomeScreen(
                            showInitialGuide = showInitialGuide,
                            onGuideComplete = {
                                preferences.edit().putBoolean("initial_guide_completed", true).apply()
                                showInitialGuide = false
                            },
                            onOpenCamera = { mode ->
                                selectedMode = mode
                                showCamera = true
                            },
                            onChangeModel = {
                                preferences.edit().remove("model_path").apply()
                                modelPath = null
                            },
                            onExit = { finish() }
                        )
                    } else {
                        OnboardingScreen(
                            initialModelPath = modelPath,
                            onContinue = { path ->
                                modelPath = path
                                preferences.edit().putString("model_path", path).apply()
                                showCamera = false
                            }
                        )
                    }
                }
            }
        }
    }
}
