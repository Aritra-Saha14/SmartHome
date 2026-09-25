package com.semhas.app.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.semhas.app.ui.components.AppButton
import com.semhas.app.ui.components.ChannelCard
import com.semhas.app.ui.components.SectionHeader
import com.semhas.app.ui.theme.Dimensions

@Composable
fun ControlScreen(
    viewModel: ControlViewModel
) {
    val state by viewModel.uiState.collectAsState()

    var newNameText by remember { mutableStateOf("") }

    // Dialog for renaming channel
    state.editingChannel?.let { channel ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRenaming() },
            title = {
                Text(
                    text = "Rename Channel ${channel.channelNumber}",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a user-friendly name for this appliance:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Dimensions.spaceSmall))
                    OutlinedTextField(
                        value = newNameText.ifEmpty { channel.applianceName },
                        onValueChange = { newNameText = it },
                        label = { Text("Appliance Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nameToSave = newNameText.ifBlank { channel.applianceName }
                        viewModel.saveRenamedChannel(channel.channelId, nameToSave)
                        newNameText = ""
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissRenaming()
                        newNameText = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

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
                title = "Channel Control",
                subtitle = "Manual relay control for up to 5 ESP32 channels"
            )

            // Master quick actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spaceMedium)
            ) {
                AppButton(
                    text = "Turn All ON",
                    onClick = { viewModel.setAllChannels(true) },
                    modifier = Modifier.weight(1f),
                    isPrimary = true
                )
                AppButton(
                    text = "Turn All OFF",
                    onClick = { viewModel.setAllChannels(false) },
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )
            }
        }

        items(state.channels, key = { it.channelId }) { channel ->
            ChannelCard(
                channel = channel,
                onToggle = { isChecked -> viewModel.setChannelState(channel.channelId, isChecked) },
                onRenameClick = {
                    newNameText = channel.applianceName
                    viewModel.startRenaming(channel)
                },
                showDetails = true,
                onIntensityChange = { newIntensity ->
                    viewModel.setChannelIntensity(channel.channelId, newIntensity)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(Dimensions.spaceLarge))
        }
    }
}
