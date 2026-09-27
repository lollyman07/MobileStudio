package com.mobilestudio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilestudio.app.ui.theme.StudioGood
import com.mobilestudio.app.ui.theme.StudioRec
import com.mobilestudio.app.ui.theme.StudioSurface
import com.mobilestudio.app.ui.theme.StudioWarn

@Composable
fun RecordingBadge(isRecording: Boolean, isPaused: Boolean, seconds: Int, resolutionLabel: String, fps: Int) {
    if (!isRecording) return
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    Column(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = if (isPaused) StudioWarn else StudioRec, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (isPaused) "PAUSED  %02d:%02d:%02d".format(h, m, s) else "REC  %02d:%02d:%02d".format(h, m, s),
                color = Color.White, fontSize = 13.sp
            )
        }
        Text("$resolutionLabel • ${fps}FPS", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
    }
}

@Composable
fun PerformancePanel(fps: Float, status: String) {
    val statusColor = when (status) {
        "Good" -> StudioGood
        "Fair" -> StudioWarn
        else -> StudioRec
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioSurface, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Performance", style = MaterialTheme.typography.labelMedium)
            Text("FPS: %.1f".format(fps), style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MicLevelMeter(levelDb: Float, muted: Boolean) {
    val fraction = ((levelDb + 60f) / 60f).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Text(if (muted) "Microphone (muted)" else "Microphone", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(StudioSurface, RoundedCornerShape(5.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (muted) 0f else fraction)
                    .background(if (fraction > 0.85f) StudioRec else StudioGood, RoundedCornerShape(5.dp))
            )
        }
        Text("%.0f dB".format(if (muted) -60f else levelDb), style = MaterialTheme.typography.bodySmall)
    }
}
