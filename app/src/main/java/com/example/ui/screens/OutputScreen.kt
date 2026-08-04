package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.data.Platform
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
                            text = "${clips.size} clips ready · ${clips.count { it.isUsed }} used",
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
                    IconButton(onClick = {
                        viewModel.startNewSession()
                        onBackToInput()
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "New Session", tint = MaterialTheme.colorScheme.primary)
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
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Button(
                            onClick = { viewModel.exportAllClips(context) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Export All to Gallery", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (clips.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = TextMuted, modifier = Modifier.size(64.dp))
                        Text("No clips yet", fontSize = 16.sp, color = TextSecondary)
                        Button(onClick = onBackToInput) { Text("Go to Input Screen") }
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
                            onMarkUsed = { viewModel.markClipAsUsed(clip.id) },
                            onTogglePlatform = { platform -> viewModel.toggleClipPlatform(clip.id, platform) },
                            onToggleField = { field -> viewModel.toggleClipField(clip.id, field) },
                            onUpdateData = { title, desc, tags -> viewModel.updateClipData(clip.id, title, desc, tags) },
                            onUpdateManualCrop = { offset -> viewModel.updateClipManualCrop(clip.id, offset) },
                            context = context
                        )
                    }
                }
            }

            activePreviewClip?.let { previewClip ->
                VideoPreviewDialog(
                    clip = previewClip,
                    onDismiss = { viewModel.showPreviewModal(null) },
                    onExport = { clipToExport -> viewModel.exportClipToStorage(context, clipToExport) }
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
    onMarkUsed: () -> Unit,
    onTogglePlatform: (Platform) -> Unit,
    onToggleField: (String) -> Unit,
    onUpdateData: (String, String, String) -> Unit,
    onUpdateManualCrop: (Float) -> Unit,
    context: Context
) {
    var editableTitle by remember(clip.title) { mutableStateOf(clip.title) }
    var editableDesc by remember(clip.description) { mutableStateOf(clip.description) }
    var editableTags by remember(clip.tags) { mutableStateOf(clip.tags.joinToString(", ")) }
    var cropSliderValue by remember(clip.manualCropOffset) { mutableFloatStateOf(clip.manualCropOffset) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (clip.isUsed)
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            else DarkSurface
        ),
        shape = RoundedCornerShape(20.dp),
        border = if (clip.isUsed)
            CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SuccessGreen.copy(alpha = 0.5f)))
        else
            CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── THUMBNAIL PREVIEW ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
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
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(54.dp))
                }

                // Play button overlay
                Box(
                    modifier = Modifier.size(58.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(36.dp))
                }

                // Viral score badge top-right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonOrange)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🔥 ${clip.viralScore}/100",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                // Timestamp badge bottom-left
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(clip.formattedTimeRange(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }

                // "USED" stamp badge top-left
                if (clip.isUsed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SuccessGreen)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("USED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }

            // ── STATUS BADGES ROW ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (clip.isCompliant) {
                    Surface(color = SuccessGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rules Compliant", fontSize = 10.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Hook/Emotion score chips
                Surface(color = ElectricViolet.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = "H:${clip.viralScore}",
                        fontSize = 10.sp,
                        color = ElectricViolet,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // ── AI HOOK TEXT ─────────────────────────────────────────────────────────
            Column {
                Text("AI Hook Overlay:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Text(clip.suggestedHookText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeonOrange)
            }

            Text("Reason: ${clip.reason}", fontSize = 12.sp, color = TextSecondary)

            // ── MANUAL CROP SLIDER ───────────────────────────────────────────────────
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Manual Crop Fine-Tuning:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Text(String.format("%.2f", cropSliderValue), fontSize = 11.sp, color = NeonOrange, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = cropSliderValue,
                    onValueChange = { cropSliderValue = it },
                    onValueChangeFinished = { onUpdateManualCrop(cropSliderValue) },
                    valueRange = -0.5f..0.5f,
                    colors = SliderDefaults.colors(thumbColor = NeonOrange, activeTrackColor = NeonOrange, inactiveTrackColor = DarkSurfaceVariant)
                )
            }

            Divider(color = DarkCardBorder)

            // ── PLATFORM SELECTION TOGGLE ROW ─────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Post to Platform:", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Platform.values().forEach { platform ->
                        val isSelected = clip.selectedPlatforms.contains(platform)
                        val platformColor = when (platform) {
                            Platform.YOUTUBE -> Color(0xFFFF0000)
                            Platform.INSTAGRAM -> Color(0xFFE1306C)
                            Platform.TIKTOK -> Color(0xFF69C9D0)
                            Platform.X -> Color(0xFF1DA1F2)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) platformColor else DarkSurfaceVariant)
                                .border(1.dp, if (isSelected) platformColor else DarkCardBorder, RoundedCornerShape(8.dp))
                                .clickable { onTogglePlatform(platform) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${platform.icon} ${platform.label}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else TextMuted
                            )
                        }
                    }
                }
            }

            // ── PLATFORM-SPECIFIC OUTPUT BLOCKS ──────────────────────────────────────

            // YOUTUBE OUTPUT
            AnimatedVisibility(
                visible = clip.selectedPlatforms.contains(Platform.YOUTUBE),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF0000).copy(alpha = 0.08f))
                        .border(1.dp, Color(0xFFFF0000).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("▶ YouTube", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF0000))
                    }

                    // Title block
                    PlatformCopyBlock(
                        label = "TITLE",
                        content = editableTitle,
                        accentColor = Color(0xFFFF0000),
                        context = context
                    )

                    // Description block
                    PlatformCopyBlock(
                        label = "DESCRIPTION",
                        content = buildString {
                            append(editableDesc)
                            if (clip.seoKeywords.isNotEmpty()) {
                                append("\n\nKeywords: ${clip.seoKeywords.joinToString(", ")}")
                            }
                        },
                        accentColor = Color(0xFFFF0000),
                        context = context
                    )

                    // SEO Keywords block
                    if (clip.seoKeywords.isNotEmpty()) {
                        PlatformCopyBlock(
                            label = "SEO KEYWORDS",
                            content = clip.seoKeywords.joinToString(", "),
                            accentColor = Color(0xFFFF0000),
                            context = context
                        )
                    }
                }
            }

            // INSTAGRAM OUTPUT
            AnimatedVisibility(
                visible = clip.selectedPlatforms.contains(Platform.INSTAGRAM),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE1306C).copy(alpha = 0.08f))
                        .border(1.dp, Color(0xFFE1306C).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("📷 Instagram", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE1306C))

                    // Caption + Tags combined (one-click copy)
                    PlatformCopyBlock(
                        label = "CAPTION + TAGS",
                        content = buildInstagramCaption(clip, editableDesc),
                        accentColor = Color(0xFFE1306C),
                        context = context
                    )
                }
            }

            // TIKTOK OUTPUT
            AnimatedVisibility(
                visible = clip.selectedPlatforms.contains(Platform.TIKTOK),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF69C9D0).copy(alpha = 0.08f))
                        .border(1.dp, Color(0xFF69C9D0).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🎵 TikTok", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2EC1D0))

                    PlatformCopyBlock(
                        label = "CAPTION + TAGS",
                        content = buildInstagramCaption(clip, editableDesc),
                        accentColor = Color(0xFF2EC1D0),
                        context = context
                    )
                }
            }

            // X (TWITTER) OUTPUT
            AnimatedVisibility(
                visible = clip.selectedPlatforms.contains(Platform.X),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1DA1F2).copy(alpha = 0.08f))
                        .border(1.dp, Color(0xFF1DA1F2).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("𝕏  X (Twitter)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1DA1F2))

                    // X uses caption + hashtags but URL instead of @handle per BOXABL rules
                    PlatformCopyBlock(
                        label = "POST TEXT",
                        content = buildString {
                            append(editableDesc.take(280))
                            val tags = clip.hashtags
                            if (tags.isNotEmpty()) append("\n${tags.take(5).joinToString(" ")}")
                        },
                        accentColor = Color(0xFF1DA1F2),
                        context = context
                    )
                }
            }

            Divider(color = DarkCardBorder)

            // ── ACTION BUTTONS ROW ────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onPlayPreview,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NeonOrange)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Preview", color = NeonOrange, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        onExport()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            // ── MARK AS USED BUTTON ───────────────────────────────────────────────────
            OutlinedButton(
                onClick = onMarkUsed,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (clip.isUsed) SuccessGreen else TextMuted
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (clip.isUsed) SuccessGreen else DarkCardBorder
                    )
                )
            ) {
                Icon(
                    imageVector = if (clip.isUsed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (clip.isUsed) SuccessGreen else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (clip.isUsed) "✓ Marked as Used / Posted" else "Mark as Used",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PlatformCopyBlock(
    label: String,
    content: String,
    accentColor: Color,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                letterSpacing = 1.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ClipForge Copy", content))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = accentColor, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor)
                }
            }
        }

        Text(
            text = content,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp
        )
    }
}

private fun buildInstagramCaption(clip: ViralClip, desc: String): String {
    return buildString {
        append(desc)
        // Add handles (@boxabl etc from rules)
        if (clip.handles.isNotEmpty()) {
            append("\n\n${clip.handles.joinToString(" ")}")
        }
        // Add hashtags
        if (clip.hashtags.isNotEmpty()) {
            append("\n${clip.hashtags.joinToString(" ")}")
        }
    }
}
