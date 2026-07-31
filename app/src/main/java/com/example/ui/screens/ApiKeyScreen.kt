package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    currentApiKey: String,
    onSaveKey: (String) -> Boolean,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var keyInput by remember { mutableStateOf(currentApiKey) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurface)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Icon Badge
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(NeonOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "API Key",
                    tint = NeonOrange,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Title & Subtitle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Welcome to ClipForge",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enter your Gemini or Vertex AI Key to power AI viral clip extraction.",
                    fontSize = 14.sp,
                    color = TextSecondary,
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
                label = { Text("Paste your Gemini or Vertex AI API Key") },
                placeholder = { Text("AIzaSy...") },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Toggle visibility",
                            tint = TextMuted
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonOrange,
                    unfocusedBorderColor = DarkCardBorder,
                    focusedLabelColor = NeonOrange,
                    unfocusedLabelColor = TextMuted,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkSurfaceVariant,
                    unfocusedContainerColor = DarkSurfaceVariant
                ),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = ErrorRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
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
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricViolet)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = ElectricViolet,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Get API Key (Google AI Studio)", color = ElectricViolet, fontWeight = FontWeight.SemiBold)
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
                colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save & Continue",
                        color = Color.Black,
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
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Stored securely on-device using EncryptedSharedPreferences",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}
