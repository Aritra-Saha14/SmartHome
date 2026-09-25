package com.semhas.app.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.semhas.app.ui.components.AppCard
import com.semhas.app.ui.components.EnergyCard
import com.semhas.app.ui.components.SectionHeader
import com.semhas.app.ui.theme.Dimensions
import com.semhas.app.utils.Formatters

@Composable
fun BillingScreen(
    viewModel: BillingViewModel
) {
    val state by viewModel.uiState.collectAsState()

    var rateInputText by remember { mutableStateOf("") }

    // Rate Edit Dialog
    if (state.isEditingRate) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissEditingRate() },
            title = {
                Text(
                    text = "Edit Electricity Rate",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        text = "Set your electricity rate in ₹ per Wh. All displayed costs will instantly recalculate based on this rate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Dimensions.spaceSmall))
                    OutlinedTextField(
                        value = rateInputText.ifEmpty { state.billing.ratePerWh.toString() },
                        onValueChange = { rateInputText = it },
                        label = { Text("Rate (₹/Wh)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = rateInputText.toDoubleOrNull() ?: state.billing.ratePerWh
                        if (parsed > 0.0) {
                            viewModel.updateRate(parsed)
                        }
                        rateInputText = ""
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissEditingRate()
                        rateInputText = ""
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
                title = "Running Estimated Bill",
                subtitle = "Calculated continuously from live consumed energy × ₹/Wh rate"
            )

            // Primary Running Bill Card
            EnergyCard(
                consumedWh = state.billing.consumedEnergyWh,
                estimatedCost = state.billing.estimatedCost,
                ratePerWh = state.billing.ratePerWh,
                period = "${state.billing.billingPeriodStart} - ${state.billing.billingPeriodEnd}",
                onEditRateClick = {
                    rateInputText = state.billing.ratePerWh.toString()
                    viewModel.startEditingRate()
                }
            )
        }

        // Daily vs Monthly Estimates
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spaceMedium)
            ) {
                AppCard(modifier = Modifier.weight(1f)) {
                    Column {
                        Text(
                            text = "Daily Cost (Today)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))
                        Text(
                            text = Formatters.formatCurrency(state.todayEnergyUsage.estimatedCost),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))
                        Text(
                            text = Formatters.formatEnergy(state.todayEnergyUsage.totalEnergy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AppCard(modifier = Modifier.weight(1f)) {
                    Column {
                        Text(
                            text = "Current Billing Period",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))
                        Text(
                            text = Formatters.formatCurrency(state.billing.estimatedCost),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))
                        Text(
                            text = Formatters.formatEnergy(state.billing.consumedEnergyWh),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Appliance-wise estimated cost breakdown
        item {
            SectionHeader(
                title = "Appliance Cost Breakdown",
                subtitle = "Running cost contribution per channel"
            )
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                if (state.channels.isEmpty()) {
                    Text(
                        text = "No appliance data available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Dimensions.spaceSmall)
                    )
                } else {
                    Column {
                        state.channels.forEachIndexed { index, channel ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = Dimensions.spaceSmall),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimensions.spaceExtraSmall),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${channel.channelNumber}. ${channel.applianceName}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Energy: ${Formatters.formatEnergy(channel.energy)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = Formatters.formatCurrency(channel.estimatedCost),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Disclaimer Card
        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                borderColor = null
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Dimensions.spaceSmall)
                    )
                    Text(
                        text = "Estimate Notice: This billing calculation is an ongoing estimate derived from measured energy consumption multiplied by your configured unit tariff. It does not include utility-specific slab structures, fixed grid charges, or municipal taxes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(Dimensions.spaceLarge))
        }
    }
}
