package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.data.ViralClip
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputScreen(
    viewModel: MainViewModel,
    onBackToInput: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val clips by viewModel.clips.collectAsState()
    val activePreviewClip by viewModel.activePreviewClip.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Generated Viral Clips",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${clips.size} clips ready",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToInput) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.startNewSession()
                            onBackToInput()
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Video Session", tint = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (clips.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = { viewModel.exportAllClips(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Export All Clips to Gallery",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (clips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MovieFilter,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Text("No generated clips yet", fontSize = 16.sp, color = TextSecondary)
                        Button(onClick = onBackToInput) {
                            Text("Go to Input Screen")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(clips, key = { it.id }) { clip ->
                        ViralClipCard(
                            clip = clip,
                            onPlayPreview = { viewModel.showPreviewModal(clip) },
                            onExport = { viewModel.exportClipToStorage(context, clip) },
                            onCopyMetadata = { viewModel.copyClipMetadataToClipboard(context, clip) },
                            onToggleField = { field -> viewModel.toggleClipField(clip.id, field) },
                            onUpdateData = { title, desc, tags ->
                                viewModel.updateClipData(clip.id, title, desc, tags)
                            },
                            onUpdateManualCrop = { offset ->
                                viewModel.updateClipManualCrop(clip.id, offset)
                            }
                        )
                    }
                }
            }

            // Preview Video Dialog
            activePreviewClip?.let { previewClip ->
                VideoPreviewDialog(
                    clip = previewClip,
                    onDismiss = { viewModel.showPreviewModal(null) },
                    onExport = { clipToExport ->
                        viewModel.exportClipToStorage(context, clipToExport)
                    }
                )
            }
        }
    }
}

@Composable
fun ViralClipCard(
    clip: ViralClip,
    onPlayPreview: () -> Unit,
    onExport: () -> Unit,
    onCopyMetadata: () -> Unit,
    onToggleField: (String) -> Unit,
    onUpdateData: (String, String, String) -> Unit,
    onUpdateManualCrop: (Float) -> Unit
) {
    var editableTitle by remember(clip.title) { mutableStateOf(clip.title) }
    var editableDesc by remember(clip.description) { mutableStateOf(clip.description) }
    var editableTags by remember(clip.tags) { mutableStateOf(clip.tags.joinToString(", ")) }
    var cropSliderValue by remember(clip.manualCropOffset) { mutableFloatStateOf(clip.manualCropOffset) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Thumbnail Preview Container (1:1 aspect)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceVariant)
                    .clickable { onPlayPreview() },
                contentAlignment = Alignment.Center
            ) {
                if (!clip.thumbnailPath.isNullOrEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(File(clip.thumbnailPath!!)),
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = NeonOrange,
                        modifier = Modifier.size(54.dp)
                    )
                }

                // Center Play Icon Button Overlay
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Confidence Badge Top Right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonOrange)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${(clip.confidenceScore * 100).toInt()}% VIRAL SCORE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                // Timestamp Badge Bottom Left
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = clip.formattedTimeRange(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            // Feature Badges Row (Campaign Compliance & Spoken Subtitles)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = ElectricViolet.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ClosedCaption, contentDescription = null, tint = ElectricViolet, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Spoken Subtitles Burned-In", fontSize = 10.sp, color = ElectricViolet, fontWeight = FontWeight.Bold)
                    }
                }

                if (clip.isCompliant) {
                    Surface(
                        color = SuccessGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rules Compliant", fontSize = 10.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Manual Crop Fine-Tuning Slider Control
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Manual Crop Fine-Tuning:",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format("%.2f", cropSliderValue),
                        fontSize = 11.sp,
                        color = NeonOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = cropSliderValue,
                    onValueChange = {
                        cropSliderValue = it
                    },
                    onValueChangeFinished = {
                        onUpdateManualCrop(cropSliderValue)
                    },
                    valueRange = -0.5f..0.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonOrange,
                        activeTrackColor = NeonOrange,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )
            }

            // Hook Text Header
            Column {
                Text(
                    text = "AI Hook Overlay:",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = clip.suggestedHookText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonOrange
                )
            }

            // Reason Text
            Text(
                text = "Reason: ${clip.reason}",
                fontSize = 12.sp,
                color = TextSecondary
            )

            // Field Toggles Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Export Metadata",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = clip.showTitle,
                        onClick = { onToggleField("title") },
                        label = { Text("Title", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = clip.showDescription,
                        onClick = { onToggleField("description") },
                        label = { Text("Desc", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = clip.showTags,
                        onClick = { onToggleField("tags") },
                        label = { Text("Tags", fontSize = 11.sp) }
                    )
                }
            }

            // Editable Title Field
            AnimatedVisibility(visible = clip.showTitle) {
                OutlinedTextField(
                    value = editableTitle,
                    onValueChange = {
                        editableTitle = it
                        onUpdateData(it, editableDesc, editableTags)
                    },
                    label = { Text("Clip Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonOrange,
                        unfocusedBorderColor = DarkCardBorder
                    )
                )
            }

            // Editable Description Field
            AnimatedVisibility(visible = clip.showDescription) {
                OutlinedTextField(
                    value = editableDesc,
                    onValueChange = {
                        editableDesc = it
                        onUpdateData(editableTitle, it, editableTags)
                    },
                    label = { Text("Description / Caption") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonOrange,
                        unfocusedBorderColor = DarkCardBorder
                    )
                )
            }

            // Editable Tags Field
            AnimatedVisibility(visible = clip.showTags) {
                OutlinedTextField(
                    value = editableTags,
                    onValueChange = {
                        editableTags = it
                        onUpdateData(editableTitle, editableDesc, it)
                    },
                    label = { Text("Hashtags (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonOrange,
                        unfocusedBorderColor = DarkCardBorder
                    )
                )
            }

            // Card Action Buttons Row: Preview, Copy Metadata & Download
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCopyMetadata,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricViolet)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = ElectricViolet, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Caption & Tags", color = ElectricViolet, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onPlayPreview,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NeonOrange)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Preview", color = NeonOrange, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onExport,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
