package com.semhas.app.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import com.semhas.app.ui.components.AppCard
import com.semhas.app.ui.components.SectionHeader
import com.semhas.app.ui.theme.Dimensions
import com.semhas.app.utils.Constants
import com.semhas.app.utils.Formatters
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel
) {
    val state by viewModel.uiState.collectAsState()
    var showSetLimitDialog by remember { mutableStateOf(false) }

    if (showSetLimitDialog) {
        var inputText by remember {
            mutableStateOf(
                if (state.monthlyBillLimit % 1.0 == 0.0) {
                    state.monthlyBillLimit.toInt().toString()
                } else {
                    state.monthlyBillLimit.toString()
                }
            )
        }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSetLimitDialog = false },
            title = {
                Text(
                    text = "Set Monthly Bill Limit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Set your desired monthly electricity bill limit in INR (${Constants.CURRENCY_SYMBOL}). The progress bar and remaining amount will track your current bill against this budget.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Dimensions.spaceMedium))
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { newText ->
                            if (newText.isEmpty() || newText.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
                                inputText = newText
                                isError = false
                            }
                        },
                        label = { Text("Monthly Limit (${Constants.CURRENCY_SYMBOL})") },
                        prefix = { Text("${Constants.CURRENCY_SYMBOL} ") },
                        isError = isError,
                        supportingText = if (isError) {
                            { Text("Please enter a valid amount greater than 0") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = inputText.toDoubleOrNull()
                        if (parsed != null && parsed > 0.0) {
                            viewModel.setMonthlyLimit(parsed)
                            showSetLimitDialog = false
                        } else {
                            isError = true
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSetLimitDialog = false }
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
            // Period Selector TabRow
            val periods = listOf(
                AnalyticsPeriod.DAILY to "Daily",
                AnalyticsPeriod.WEEKLY to "Weekly",
                AnalyticsPeriod.MONTHLY to "Monthly"
            )
            val selectedIndex = periods.indexOfFirst { it.first == state.selectedPeriod }

            TabRow(
                selectedTabIndex = if (selectedIndex >= 0) selectedIndex else 0,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                periods.forEachIndexed { _, (period, label) ->
                    Tab(
                        selected = state.selectedPeriod == period,
                        onClick = { viewModel.selectPeriod(period) },
                        text = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    )
                }
            }
        }

        // MONTH SELECTOR (Requirement 9): Interactive month navigation and chips
        if (state.selectedPeriod == AnalyticsPeriod.MONTHLY) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.previousMonth() }) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous Month",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(end = 4.dp)
                                )
                                Text(
                                    text = state.selectedMonth.format(
                                        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(onClick = { viewModel.nextMonth() }) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next Month",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Selectable quick month chips (August 2026, September 2026, etc.)
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Dimensions.spaceExtraSmall),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.availableMonths) { ym ->
                                val isSelected = ym == state.selectedMonth
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectMonth(ym) },
                                    label = {
                                        Text(
                                            text = ym.format(
                                                DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
                                            ),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Period Header Label
        item {
            val periodSubtitle = when (state.selectedPeriod) {
                AnalyticsPeriod.DAILY -> state.periodData.periodLabel.ifEmpty { "Today's consumption" }
                AnalyticsPeriod.WEEKLY -> state.periodData.periodLabel.ifEmpty { "Current week (Mon - Sun)" }
                AnalyticsPeriod.MONTHLY -> state.periodData.periodLabel.ifEmpty { "Monthly breakdown" }
            }

            SectionHeader(
                title = "${state.selectedPeriod.name.lowercase().replaceFirstChar { it.uppercase() }} Overview",
                subtitle = periodSubtitle
            )
        }

        // Period Consumption Overview Card
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Period Consumption",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = Formatters.formatEnergy(state.periodData.totalEnergyWh),
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Estimated Cost",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = Formatters.formatCurrency(state.periodData.totalCost),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.spaceMedium))

                    // Highest Consuming Appliance Highlight
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(Dimensions.radiusSmall)
                            )
                            .padding(Dimensions.spaceSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = Dimensions.spaceSmall)
                        )
                        Column {
                            Text(
                                text = "Highest Consuming Appliance",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${state.periodData.highestApplianceName} (${Formatters.formatEnergy(state.periodData.highestApplianceEnergy)})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // MONTHLY USAGE LIMIT (Task 2)
        if (state.selectedPeriod == AnalyticsPeriod.MONTHLY) {
            item {
                SectionHeader(
                    title = "Monthly Usage Limit",
                    subtitle = "Track monthly expenses against user-defined limit"
                )
            }

            item {
                val currentBill = state.periodData.totalCost
                val limit = state.monthlyBillLimit
                val remaining = (limit - currentBill).coerceAtLeast(0.0)
                val progressFraction = if (limit > 0.0) (currentBill / limit).toFloat().coerceIn(0f, 1f) else 0f
                val progressPercent = if (limit > 0.0) Math.round((currentBill / limit) * 100.0).toInt() else 0
                val isLimitExceeded = limit > 0.0 && currentBill >= limit

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        // 3-column metric layout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.spaceSmall)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Monthly Bill Limit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = Formatters.formatCurrency(limit),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Current Bill",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = Formatters.formatCurrency(currentBill),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLimitExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Remaining",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = Formatters.formatCurrency(remaining),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remaining == 0.0 && isLimitExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimensions.spaceMedium))

                        // Progress Bar & Percentage Label
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isLimitExceeded) "Limit Reached ($progressPercent%)" else "Progress: $progressPercent%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isLimitExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isLimitExceeded) {
                                Text(
                                    text = "Over by ${Formatters.formatCurrency(currentBill - limit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        if (isLimitExceeded) MaterialTheme.colorScheme.error
                                        else if (progressFraction > 0.8f) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.primary
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(Dimensions.spaceMedium))

                        // Action Button
                        OutlinedButton(
                            onClick = { showSetLimitDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(Dimensions.spaceSmall))
                            Text("Set Monthly Limit")
                        }
                    }
                }
            }
        }

        // HISTORICAL BILLING OVERVIEW CARDS (Requirement 5 & 21)
        item {
            SectionHeader(
                title = "Persistent Billing Breakdown",
                subtitle = "Cumulative billing totals stored securely in Supabase"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spaceSmall)
            ) {
                // Today Card
                AppCard(modifier = Modifier.weight(1f)) {
                    Column {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Formatters.formatCurrency(state.billingSummary.todayCost),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = Formatters.formatEnergy(state.billingSummary.todayEnergyWh),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Weekly Card
                AppCard(modifier = Modifier.weight(1f)) {
                    Column {
                        Text(
                            text = "This Week",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Formatters.formatCurrency(state.billingSummary.weeklyCost),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = Formatters.formatEnergy(state.billingSummary.weeklyEnergyWh),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.spaceSmall))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spaceSmall)
            ) {
                // Monthly Card
                AppCard(modifier = Modifier.weight(1f)) {
                    Column {
                        Text(
                            text = "This Month",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Formatters.formatCurrency(state.billingSummary.monthlyCost),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = Formatters.formatEnergy(state.billingSummary.monthlyEnergyWh),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // All-Time Card
                AppCard(modifier = Modifier.weight(1f)) {
                    Column {
                        Text(
                            text = "All-Time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Formatters.formatCurrency(state.billingSummary.allTimeCost),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = Formatters.formatEnergy(state.billingSummary.allTimeEnergyWh),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Consumption Trend Chart Card (Requirements 10, 11, 22)
        item {
            SectionHeader(
                title = "Consumption Trend",
                subtitle = when (state.selectedPeriod) {
                    AnalyticsPeriod.DAILY -> "Today's hourly usage profile"
                    AnalyticsPeriod.WEEKLY -> "Daily breakdown: Mon, Tue, Wed, Thu, Fri, Sat, Sun"
                    AnalyticsPeriod.MONTHLY -> "Weekly profile across ${state.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))}"
                }
            )

            AppCard(modifier = Modifier.fillMaxWidth()) {
                // EMPTY STATE CHECK (Requirement 22):
                // If selected date/month has no recorded consumption, show dedicated empty state
                if (!state.periodData.hasData || state.periodData.totalEnergyWh <= 0.0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimensions.spaceLarge),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(Dimensions.spaceSmall))
                        Text(
                            text = "No recorded consumption for this period",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Appliance usage during this period will automatically appear here once recorded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    AnalyticsChart(
                        period = state.selectedPeriod,
                        trends = state.periodData.trends
                    )
                }
            }
        }

        // APPLIANCE COMPARISON BREAKDOWN (Requirement 12 & 21)
        item {
            SectionHeader(
                title = "Comparative Appliance Usage",
                subtitle = "CH1 to CH5 historical consumption for selected period"
            )
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                val breakdown = state.periodData.applianceBreakdown
                if (breakdown.isEmpty() || !state.periodData.hasData) {
                    Text(
                        text = "No appliance data recorded for this period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Dimensions.spaceSmall)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spaceMedium)) {
                        breakdown.forEach { item ->
                            val shareFraction = (item.percentage / 100.0).toFloat().coerceIn(0f, 1f)

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CH${item.channelNumber}. ${item.applianceName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${Formatters.formatEnergy(item.energyWh)} (${if (item.percentage <= 0.0) 0 else Math.round(item.percentage).toInt()}%)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(shareFraction)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Estimated cost: ${Formatters.formatCurrency(item.cost)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(Dimensions.spaceLarge))
        }
    }
}

@Composable
private fun AnalyticsChart(
    period: AnalyticsPeriod,
    trends: List<Pair<String, Double>>
) {
    if (trends.isEmpty()) return

    val maxVal = (trends.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(0.001)

    when (period) {
        AnalyticsPeriod.DAILY -> {
            // Mobile-friendly 24-hour horizontal scrolling view ensuring every time label and Wh value is visible
            val currentHour = remember { LocalTime.now().hour }
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = (currentHour - 2).coerceAtLeast(0)
            )

            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(top = Dimensions.spaceSmall),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                items(trends) { (label, value) ->
                    val isCurrentHour = label.toIntOrNull() == currentHour
                    AnalyticsBarItem(
                        label = label,
                        value = value,
                        maxVal = maxVal,
                        barWidth = 18.dp,
                        columnWidth = 46.dp,
                        isHighlighted = isCurrentHour
                    )
                }
            }
        }
        AnalyticsPeriod.WEEKLY -> {
            // Exactly 7 days (Mon, Tue, Wed, Thu, Fri, Sat, Sun) evenly spaced across screen width
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(top = Dimensions.spaceSmall),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                trends.forEach { (label, value) ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        AnalyticsBarItem(
                            label = label,
                            value = value,
                            maxVal = maxVal,
                            barWidth = 20.dp,
                            columnWidth = null
                        )
                    }
                }
            }
        }
        AnalyticsPeriod.MONTHLY -> {
            // Exactly 4 weeks (Week 1, Week 2, Week 3, Week 4) evenly spaced across screen width
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(top = Dimensions.spaceSmall),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                trends.forEach { (label, value) ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        AnalyticsBarItem(
                            label = label,
                            value = value,
                            maxVal = maxVal,
                            barWidth = 28.dp,
                            columnWidth = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsBarItem(
    label: String,
    value: Double,
    maxVal: Double,
    barWidth: Dp,
    columnWidth: Dp? = null,
    isHighlighted: Boolean = false
) {
    val plotAreaHeight = 110.dp
    val barFraction = if (maxVal > 0.0) (value / maxVal).toFloat().coerceIn(0f, 1f) else 0f
    val barHeight = if (value > 0.0) (plotAreaHeight * barFraction).coerceAtLeast(4.dp) else 0.dp

    val modifier = if (columnWidth != null) {
        Modifier.width(columnWidth)
    } else {
        Modifier.fillMaxWidth()
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP: Value Label Zone (Reserved 22.dp area)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = Formatters.formatEnergy(value),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (columnWidth != null) 9.sp else 10.sp,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // MIDDLE: Plot Area (Reserved 110.dp area) - Green bar is strictly constrained here!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotAreaHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(barHeight)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(
                        if (value > 0.0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // BOTTOM: X-Axis Label Zone (Reserved 22.dp area) - NEVER clipped or covered!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

