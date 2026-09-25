package com.semhas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.semhas.app.ui.theme.Dimensions
import com.semhas.app.ui.theme.StatusActive
import com.semhas.app.ui.theme.StatusDisconnected
import com.semhas.app.ui.theme.StatusError
import com.semhas.app.ui.theme.StatusInactive
import com.semhas.app.ui.theme.StatusWarning

enum class StatusType {
    ACTIVE,
    INACTIVE,
    CONNECTED,
    DISCONNECTED,
    WARNING,
    ERROR
}

@Composable
fun StatusIndicator(
    status: StatusType,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    val (bgColor, contentColor, icon, defaultText) = when (status) {
        StatusType.ACTIVE -> Quad(
            StatusActive.copy(alpha = 0.15f),
            StatusActive,
            Icons.Default.PowerSettingsNew,
            "ON"
        )
        StatusType.INACTIVE -> Quad(
            StatusInactive.copy(alpha = 0.15f),
            StatusInactive,
            Icons.Default.PowerSettingsNew,
            "OFF"
        )
        StatusType.CONNECTED -> Quad(
            StatusActive.copy(alpha = 0.15f),
            StatusActive,
            Icons.Default.Wifi,
            "Connected"
        )
        StatusType.DISCONNECTED -> Quad(
            StatusDisconnected.copy(alpha = 0.15f),
            StatusDisconnected,
            Icons.Default.WifiOff,
            "Disconnected"
        )
        StatusType.WARNING -> Quad(
            StatusWarning.copy(alpha = 0.15f),
            StatusWarning,
            Icons.Default.Warning,
            "Warning"
        )
        StatusType.ERROR -> Quad(
            StatusError.copy(alpha = 0.15f),
            StatusError,
            Icons.Default.Error,
            "Error"
        )
    }

    Row(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(Dimensions.radiusSmall))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label ?: defaultText,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
