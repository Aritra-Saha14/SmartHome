package com.semhas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semhas.app.ui.theme.Dimensions

/**
 * Compact horizontal stepped intensity / speed control for CH4 & CH5.
 *
 * Supports 4 discrete steps: 25%, 50%, 75%, 100%.
 * Visually highlighted with SEMHAS Green for the active selection.
 */
@Composable
fun IntensityControl(
    currentIntensity: Int,
    onIntensitySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Speed / Intensity",
    enabled: Boolean = true
) {
    val steps = listOf(25, 50, 75, 100)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Dimensions.spaceSmall)
    ) {
        // Label & Current Value Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$currentIntensity%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Stepped horizontal row: [ 25% ] [ 50% ] [ 75% ] [ 100% ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEach { step ->
                val isSelected = currentIntensity == step
                val backgroundColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }

                val textColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                val borderColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(Dimensions.radiusSmall))
                        .background(backgroundColor)
                        .border(1.dp, borderColor, RoundedCornerShape(Dimensions.radiusSmall))
                        .clickable(enabled = enabled) {
                            onIntensitySelected(step)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$step%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = textColor
                    )
                }
            }
        }
    }
}
