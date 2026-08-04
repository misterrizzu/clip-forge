package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProcessingPipelineStatus
import com.example.ui.MainViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoInputScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onGenerationComplete: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val selectedTab by viewModel.selectedTab.collectAsState()
    val localVideoUri by viewModel.localVideoUri.collectAsState()
    val localVideoName by viewModel.localVideoName.collectAsState()
    val gdriveUrl by viewModel.gdriveUrl.collectAsState()
    val rulesFileName by viewModel.rulesFileName.collectAsState()
    val customInstructions by viewModel.customInstructions.collectAsState()
    val processingStatus by viewModel.processingStatus.collectAsState()
    val batchQueue by viewModel.batchQueue.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val showHookBanner by viewModel.showHookBanner.collectAsState()
    val showSubtitlesBanner by viewModel.showSubtitlesBanner.collectAsState()
    val campaignRulePresets by viewModel.campaignRulePresets.collectAsState()
    val activePresetId by viewModel.activePresetId.collectAsState()

    // Pending rules text waiting to be named and saved as preset
    var pendingRulesText by remember { mutableStateOf("") }
    var pendingRulesFileName by remember { mutableStateOf("") }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    // File pickers
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "Selected_Video.mp4"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            viewModel.setLocalVideo(uri, fileName)
            viewModel.resetProcessingStatus()
        }
    }

    val rulesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "Rules_Document"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            // Read the file and open the Save Preset dialog
            viewModel.readAndSetRulesFile(context, uri, fileName)
            pendingRulesFileName = fileName
            presetNameInput = fileName.removeSuffix(".pdf").removeSuffix(".txt").removeSuffix(".docx")
            showSavePresetDialog = true
        }
    }

    LaunchedEffect(processingStatus) {
        if (processingStatus is ProcessingPipelineStatus.Success) {
            onGenerationComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ClipForge",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Project History Folders"
                        )
                    }

                    IconButton(onClick = { viewModel.toggleTheme() }) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.WbSunny else Icons.Default.NightsStay,
                            contentDescription = "Toggle Theme"
                        )
                    }

                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Section 1: Video Input Source Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Video Source",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Compact Capsule Selector Buttons
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Capsule Button 0: Upload Video
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selectedTab == 0) NeonOrange else Color.Transparent)
                                .clickable {
                                    viewModel.setSelectedTab(0)
                                    viewModel.resetProcessingStatus()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Browse File",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Capsule Button 1: Video Link
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selectedTab == 1) NeonOrange else Color.Transparent)
                                .clickable {
                                    viewModel.setSelectedTab(1)
                                    viewModel.resetProcessingStatus()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Video Link",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Tab Contents
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(modifier = Modifier.padding(18.dp)) {
                        when (selectedTab) {
                            0 -> { // Local Upload Video
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (localVideoUri == null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { videoPickerLauncher.launch("video/*") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Default.VideoFile,
                                                    contentDescription = null,
                                                    tint = NeonOrange,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text("Tap to browse local video file", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                                Text("MP4, MOV, MKV formats supported", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                            }
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(14.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = SuccessGreen,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = localVideoName ?: "Video Selected",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text("Ready for AI clip generation", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            IconButton(onClick = { videoPickerLauncher.launch("video/*") }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Change", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }

                            1 -> { // Google Drive Link
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = gdriveUrl,
                                        onValueChange = {
                                            viewModel.setGdriveUrl(it)
                                            viewModel.resetProcessingStatus()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Paste Google Drive Link") },
                                        placeholder = { Text("https://drive.google.com/file/d/...") },
                                        singleLine = true,
                                        leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = ElectricViolet) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ElectricViolet,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Text(
                                        text = "Note: Ensure file sharing permission is set to 'Anyone with the link can view'.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: Campaign Rules Presets
                Text(
                    text = "Campaign Rules",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Saved Presets horizontal scrollable chips row
                if (campaignRulePresets.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(campaignRulePresets) { preset ->
                            val isActive = preset.id == activePresetId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isActive) ElectricViolet else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (isActive) ElectricViolet else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable { viewModel.selectRulesPreset(preset) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (isActive) Color.White else ElectricViolet,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = preset.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Deselect",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { viewModel.clearRulesPresetSelection() }
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Preset",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { viewModel.deleteRulesPreset(preset.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add New Preset button
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (activePresetId != null) ElectricViolet.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rulesPickerLauncher.launch("*/*") }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (activePresetId != null) ElectricViolet.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (activePresetId != null) Icons.Default.Verified else Icons.Default.Add,
                                contentDescription = null,
                                tint = ElectricViolet,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (activePresetId != null) rulesFileName ?: "Rules Loaded"
                                       else "Upload Campaign Rules File",
                                fontWeight = FontWeight.SemiBold,
                                color = if (activePresetId != null) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (activePresetId != null) "✓ Active — AI will follow these rules"
                                       else "PDF, TXT, DOCX — saved as reusable preset",
                                fontSize = 11.sp,
                                color = if (activePresetId != null) ElectricViolet
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            tint = ElectricViolet,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Save Preset Dialog
                if (showSavePresetDialog) {
                    AlertDialog(
                        onDismissRequest = { showSavePresetDialog = false },
                        title = { Text("Save as Campaign Preset", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Give this rules file a name so you can reuse it across campaigns:",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = presetNameInput,
                                    onValueChange = { presetNameInput = it },
                                    label = { Text("Preset Name (e.g. BOXABL Campaign)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricViolet
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val rulesText = viewModel.rulesFileContent.value
                                    if (rulesText.isNotBlank()) {
                                        viewModel.saveRulesPreset(
                                            name = presetNameInput,
                                            rulesText = rulesText,
                                            fileName = pendingRulesFileName
                                        )
                                    }
                                    showSavePresetDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                            ) {
                                Text("Save Preset", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSavePresetDialog = false }) {
                                Text("Use Once (Don't Save)")
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                // Section 3: Custom Instructions
                Text(
                    text = "Custom Instructions (Optional)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = customInstructions,
                    onValueChange = { viewModel.setCustomInstructions(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    placeholder = { Text("e.g., 'focus on emotional moments, high energy quotes, funny interactions'") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                // Section 4: Batch Processing Queue
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Batch Queue (${batchQueue.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { viewModel.addToBatchQueue() }) {
                        Icon(Icons.Default.Queue, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Queue Current Video", color = NeonOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (batchQueue.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        batchQueue.forEach { task ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (task.isGDrive) Icons.Default.CloudDownload else Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = ElectricViolet,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(task.fileName, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Status: ${task.status.name}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { viewModel.removeBatchQueueTask(task.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Error Banner with Retry Button
                if (processingStatus is ProcessingPipelineStatus.Error) {
                    val errMessage = (processingStatus as ProcessingPipelineStatus.Error).message
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = errMessage,
                                    color = ErrorRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.startClipGeneration(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Generate Button
                Button(
                    onClick = { viewModel.startClipGeneration(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                    shape = RoundedCornerShape(16.dp),
                    enabled = processingStatus !is ProcessingPipelineStatus.Processing
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Generate Viral Clips",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Processing Pipeline Fullscreen Overlay
            if (processingStatus is ProcessingPipelineStatus.Processing) {
                val status = processingStatus as ProcessingPipelineStatus.Processing
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.88f))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { status.progressFraction },
                                color = NeonOrange,
                                strokeWidth = 5.dp,
                                modifier = Modifier.size(60.dp)
                            )
                            Text(
                                text = "ClipForge Real Pipeline",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = status.stepText,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            LinearProgressIndicator(
                                progress = { status.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = NeonOrange,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }

            // Pre-Render Clip Selection Dialog
            if (processingStatus is ProcessingPipelineStatus.ReviewCandidateClips) {
                val reviewStatus = processingStatus as ProcessingPipelineStatus.ReviewCandidateClips
                ClipSelectionDialog(
                    candidateClips = reviewStatus.candidates,
                    videoFile = reviewStatus.videoFile,
                    onConfirmSelection = { approvedClips ->
                        viewModel.confirmAndRenderSelectedClips(approvedClips, reviewStatus.videoFile)
                    },
                    onDismiss = {
                        viewModel.resetProcessingStatus()
                    }
                )
            }
        }
    }
}
