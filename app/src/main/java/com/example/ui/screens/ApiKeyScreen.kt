package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyScreen(
    viewModel: MainViewModel,
    currentApiKey: String,
    onSaveKey: (String) -> Boolean,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val enablePreRenderReview by viewModel.enablePreRenderReview.collectAsState()

    var keyInput by remember { mutableStateOf(currentApiKey) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header Icon Badge
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "API Key",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Title & Subtitle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Welcome to ClipForge",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Configure your Gemini API key and app settings.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Input Field
            OutlinedTextField(
                value = keyInput,
                onValueChange = {
                    keyInput = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Paste your Gemini API Key") },
                placeholder = { Text("AIzaSy...") },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Toggle visibility",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Divider(color = MaterialTheme.colorScheme.outline)

            val showHookBanner by viewModel.showHookBanner.collectAsState()
            val showSubtitlesBanner by viewModel.showSubtitlesBanner.collectAsState()
            val subSettings by viewModel.subtitleSettings.collectAsState()

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Canvas Overlay & Banner Settings",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Toggle 1: Top Hook Text Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Top Hook Text Banner",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (showHookBanner) "Shows bold hook banner (OFF uses pillarbox blur)" else "Pillarbox blur effect enabled (OFF)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showHookBanner,
                        onCheckedChange = { viewModel.setShowHookBanner(it) }
                    )
                }

                // Toggle 2: Bottom Subtitles Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Spoken Dialogue Subtitles",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Burn in subtitles via Android Canvas API",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showSubtitlesBanner,
                        onCheckedChange = { viewModel.setShowSubtitlesBanner(it) }
                    )
                }

                // Toggle 3: Pre-Render Approval
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pre-Render Clip Approval Step",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Approve candidate clips before rendering video",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enablePreRenderReview,
                        onCheckedChange = { viewModel.setEnablePreRenderReview(it) }
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outline)

                Text(
                    text = "Subtitle Styling (Canvas API)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Subtitle Font Size Chips
                Column {
                    Text("Font Size:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.data.SubtitleFontSize.values().forEach { size ->
                            FilterChip(
                                selected = subSettings.fontSize == size,
                                onClick = { viewModel.updateSubtitleSettings(subSettings.copy(fontSize = size)) },
                                label = { Text(size.label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Subtitle Text Color Chips
                Column {
                    Text("Text Color:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.data.SubtitleTextColor.values().forEach { color ->
                            FilterChip(
                                selected = subSettings.textColor == color,
                                onClick = { viewModel.updateSubtitleSettings(subSettings.copy(textColor = color)) },
                                label = { Text(color.label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Subtitle Background Opacity Chips
                Column {
                    Text("Background Opacity:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.data.SubtitleBgOpacity.values().forEach { opacity ->
                            FilterChip(
                                selected = subSettings.bgOpacity == opacity,
                                onClick = { viewModel.updateSubtitleSettings(subSettings.copy(bgOpacity = opacity)) },
                                label = { Text(opacity.label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Subtitle Position Chips
                Column {
                    Text("Position:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.data.SubtitlePosition.values().forEach { pos ->
                            FilterChip(
                                selected = subSettings.position == pos,
                                onClick = { viewModel.updateSubtitleSettings(subSettings.copy(position = pos)) },
                                label = { Text(pos.label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Button 1: Get API Key
            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://aistudio.google.com/app/apikey")
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Get API Key (Google AI Studio)", fontWeight = FontWeight.SemiBold)
            }

            // Button 2: Save & Continue
            Button(
                onClick = {
                    keyboardController?.hide()
                    if (keyInput.isBlank()) {
                        errorMessage = "API key cannot be empty."
                        return@Button
                    }
                    val valid = onSaveKey(keyInput)
                    if (valid) {
                        isSuccess = true
                        onContinue()
                    } else {
                        errorMessage = "Invalid key format. Please enter a valid Gemini API key."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save & Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Security Badge Note
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Encrypted",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Stored securely on-device using EncryptedSharedPreferences",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
