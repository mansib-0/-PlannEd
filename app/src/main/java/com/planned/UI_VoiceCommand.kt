package com.planned

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat

// ─────────────────────────────────────────────────────────────────
// VoiceMicButton — drop this composable anywhere in a layout
// ─────────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun VoiceMicButton(
    db: AppDatabase,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // UI state driven by VoiceCommandManager callbacks
    var phase by remember { mutableStateOf(VoicePhase.IDLE) }
    var lastResult by remember { mutableStateOf<VoiceResult?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }

    // Wire up manager callbacks once per composition
    DisposableEffect(Unit) {
        VoiceCommandManager.initTts(context)
        VoiceCommandManager.onPhaseChange = { newPhase -> phase = newPhase }
        VoiceCommandManager.onResult = { result ->
            lastResult = result
            showResultDialog = true
        }
        onDispose {
            VoiceCommandManager.releaseTts()
        }
    }

    // Mic permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) VoiceCommandManager.startListening(context, db)
        else phase = VoicePhase.ERROR
    }

    // Pulse animation while listening
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.30f,
        animationSpec = infiniteRepeatable(
            animation  = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Button colour per phase
    val buttonColor = when (phase) {
        VoicePhase.IDLE      -> PrimaryColor
        VoicePhase.LISTENING -> Color(0xFFE53935)   // red
        VoicePhase.THINKING  -> Color(0xFFFB8C00)   // orange
        VoicePhase.SPEAKING  -> Color(0xFF43A047)   // green
        VoicePhase.ERROR     -> Color(0xFF9E9E9E)   // grey
    }

    val isActive      = phase != VoicePhase.IDLE && phase != VoicePhase.ERROR
    val appliedScale  = if (phase == VoicePhase.LISTENING) pulseScale else 1f

    Box(
        modifier        = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Ripple ring — only while active
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .scale(appliedScale)
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = 0.18f))
            )
        }

        // Main mic button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true)
                ) {
                    when (phase) {
                        VoicePhase.IDLE, VoicePhase.ERROR -> {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                VoiceCommandManager.startListening(context, db)
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        VoicePhase.LISTENING -> {
                            phase = VoicePhase.THINKING
                            VoiceCommandManager.stopListening()
                        }
                        VoicePhase.SPEAKING -> {
                            VoiceCommandManager.cancelSpeech()
                        }
                        VoicePhase.THINKING -> { /* cannot interrupt mid-API call */ }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector      = if (phase == VoicePhase.ERROR) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = when (phase) {
                    VoicePhase.IDLE      -> "Start voice command"
                    VoicePhase.LISTENING -> "Stop recording"
                    VoicePhase.THINKING  -> "Thinking…"
                    VoicePhase.SPEAKING  -> "Stop speaking"
                    VoicePhase.ERROR     -> "Voice unavailable"
                },
                tint     = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    // Result dialog
    if (showResultDialog) {
        lastResult?.let { result ->
            VoiceResultDialog(
                result    = result,
                onDismiss = { showResultDialog = false }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// VoiceResultDialog — shown after every command
// ─────────────────────────────────────────────────────────────────
@Composable
fun VoiceResultDialog(
    result:    VoiceResult,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape    = RoundedCornerShape(20.dp),
            colors   = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ── Title ────────────────────────────────────────
                Text(
                    text       = "PlannEd Assistant",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = PrimaryColor
                )

                HorizontalDivider(color = Color.LightGray, thickness = 0.8.dp)

                // ── What the user said ────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text       = "You said",
                        fontSize   = 11.sp,
                        color      = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF5F5F5))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text      = "\u201C${result.userText}\u201D",
                            fontSize  = 13.sp,
                            color     = Color.DarkGray,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }

                // ── PlannEd's reply ───────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text       = "PlannEd",
                        fontSize   = 11.sp,
                        color      = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text       = result.replyText,
                        fontSize   = 14.sp,
                        color      = Color.Black,
                        lineHeight = 20.sp
                    )
                }

                // ── Action badge (only when something was created) ─
                result.actionTaken?.let { action ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryColor.copy(alpha = 0.10f))
                            .border(1.dp, PrimaryColor.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text       = "\u2713 $action",
                            fontSize   = 12.sp,
                            color      = PrimaryColor,
                            fontWeight = FontWeight.SemiBold,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ── Dismiss ───────────────────────────────────────
                TextButton(
                    onClick  = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Done", color = PrimaryColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// VoiceStatusBanner — optional floating label at bottom of screen
// Place inside the Scaffold content Box, below the main content.
// ─────────────────────────────────────────────────────────────────
@Composable
fun VoiceStatusBanner(phase: VoicePhase) {
    if (phase == VoicePhase.IDLE || phase == VoicePhase.ERROR) return

    val label = when (phase) {
        VoicePhase.LISTENING -> "Listening…"
        VoicePhase.THINKING  -> "Thinking…"
        VoicePhase.SPEAKING  -> "Speaking…"
        else                 -> return
    }

    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text       = label,
                color      = Color.White,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
