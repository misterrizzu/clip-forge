package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
            viewModel.readAndSetRulesFile(context, uri, fileName)
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
                // Section 1: Video Input Source Tabs
                Text(
                    text = "Select Video Source",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkSurface,
                    contentColor = NeonOrange,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            viewModel.setSelectedTab(0)
                            viewModel.resetProcessingStatus()
                        },
                        text = { Text("Upload Video", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            viewModel.setSelectedTab(1)
                            viewModel.resetProcessingStatus()
                        },
                        text = { Text("Google Drive Link", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                // Tab Contents
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder))
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
                                                .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
                                                .background(DarkSurfaceVariant)
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
                                                Text("Tap to browse local video file", color = TextPrimary, fontSize = 14.sp)
                                                Text("MP4, MOV, MKV formats supported", color = TextMuted, fontSize = 11.sp)
                                            }
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(DarkSurfaceVariant)
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
                                                    color = TextPrimary,
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text("Ready for AI clip generation", fontSize = 12.sp, color = TextSecondary)
                                            }
                                            IconButton(onClick = { videoPickerLauncher.launch("video/*") }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Change", tint = TextMuted)
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
                                            unfocusedBorderColor = DarkCardBorder,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            focusedContainerColor = DarkSurfaceVariant,
                                            unfocusedContainerColor = DarkSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Text(
                                        text = "Note: Ensure file sharing permission is set to 'Anyone with the link can view'.",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: Upload Campaign Rules File
                Text(
                    text = "Campaign Rules Document (Optional)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rulesPickerLauncher.launch("*/*") }
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(ElectricViolet.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = ElectricViolet,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rulesFileName ?: "Upload Campaign Rules (PDF/DOCX/TXT)",
                                fontWeight = FontWeight.SemiBold,
                                color = if (rulesFileName != null) TextPrimary else TextSecondary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (rulesFileName != null) "Rules loaded & ready" else "AI will prioritize topics matching your rules",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        if (rulesFileName != null) {
                            IconButton(onClick = { viewModel.clearRulesFile() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = ErrorRed)
                            }
                        } else {
                            Icon(Icons.Default.Add, contentDescription = null, tint = ElectricViolet)
                        }
                    }
                }

                // Section 3: Aspect Ratio & Duration Settings
                Text(
                    text = "Clip Output Settings",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Target Aspect Ratio:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )

                        val currentRatio by viewModel.aspectRatio.collectAsState()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            com.example.data.AspectRatio.values().forEach { ratio ->
                                val selected = ratio == currentRatio
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setAspectRatio(ratio) },
                                    label = { Text(ratio.label, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonOrange,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outline)

                        val minDur by viewModel.minDurationSeconds.collectAsState()
                        val maxDur by viewModel.maxDurationSeconds.collectAsState()
                        var sliderPosition by remember(minDur, maxDur) { mutableStateOf(minDur.toFloat()..maxDur.toFloat()) }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Clip Length Range (Drag Min & Max):",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${sliderPosition.start.toInt()}s — ${sliderPosition.endInclusive.toInt()}s",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            RangeSlider(
                                value = sliderPosition,
                                onValueChange = { range ->
                                    sliderPosition = range
                                    viewModel.setClipDurationRange(range.start.toInt(), range.endInclusive.toInt())
                                },
                                valueRange = 10f..120f,
                                steps = 21,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outline)

                        Text(
                            text = "9:16 Canvas Overlays:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Top Hook Text Banner",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Show AI hook text banner on top empty space",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showHookBanner,
                                onCheckedChange = { viewModel.setShowHookBanner(it) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Bottom Subtitles Banner",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Burn in spoken dialogue subtitles on bottom space",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showSubtitlesBanner,
                                onCheckedChange = { viewModel.setShowSubtitlesBanner(it) }
                            )
                        }
                    }
                }

                // Section 4: Custom Instructions
                Text(
                    text = "Custom Instructions (Optional)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
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
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
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
                        color = TextPrimary
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
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
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
                                        Text(task.fileName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Status: ${task.status.name}", color = TextSecondary, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { viewModel.removeBatchQueueTask(task.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
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
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
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
                                color = TextPrimary
                            )
                            Text(
                                text = status.stepText,
                                fontSize = 14.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            LinearProgressIndicator(
                                progress = { status.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = NeonOrange,
                                trackColor = DarkSurfaceVariant
                            )
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
