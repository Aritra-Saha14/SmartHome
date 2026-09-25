package com.semhas.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.semhas.app.data.model.HistoryEvent
import com.semhas.app.ui.components.AppCard
import com.semhas.app.ui.components.SectionHeader
import com.semhas.app.ui.theme.Dimensions
import com.semhas.app.utils.Formatters

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel
) {
    val state by viewModel.uiState.collectAsState()

    if (state.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimensions.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spaceMedium)
    ) {
        item {
            Spacer(modifier = Modifier.height(Dimensions.spaceSmall))
            SectionHeader(
                title = "Event History",
                subtitle = "Logged control events, alerts, and operational milestones"
            )

            // Filter Chips Row
            val filters = listOf(
                "ALL" to "All Events",
                "CONTROL" to "Controls",
                "ALERT" to "Alerts",
                "SYSTEM" to "System"
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.spaceSmall)) {
                items(filters) { (filterKey, label) ->
                    val isSelected = state.selectedFilter == filterKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectFilter(filterKey) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        if (state.events.isEmpty()) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No events logged for this filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(state.events, key = { it.id }) { event ->
                HistoryEventItem(event = event)
            }
        }

        item {
            Spacer(modifier = Modifier.height(Dimensions.spaceLarge))
        }
    }
}

@Composable
private fun HistoryEventItem(event: HistoryEvent) {
    val icon = when (event.eventType.uppercase()) {
        "CONTROL" -> Icons.Default.PowerSettingsNew
        "ALERT" -> Icons.Default.Warning
        "ENERGY" -> Icons.Default.Bolt
        else -> Icons.Default.Info
    }

    val iconColor = when (event.eventType.uppercase()) {
        "CONTROL" -> MaterialTheme.colorScheme.primary
        "ALERT" -> MaterialTheme.colorScheme.error
        "ENERGY" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.padding(end = Dimensions.spaceMedium)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))
                Text(
                    text = Formatters.formatTimestamp(event.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
