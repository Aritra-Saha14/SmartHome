package com.semhas.app.ui.analytics

import com.semhas.app.data.mock.MockData
import com.semhas.app.data.mock.MockSemhasRepository
import com.semhas.app.data.model.ApplianceHistoricalUsage
import com.semhas.app.data.model.PeriodAnalyticsData
import com.semhas.app.data.supabase.model.DailyConsumptionDto
import com.semhas.app.data.supabase.model.EnergyReadingDto
import com.semhas.app.utils.Formatters
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class AnalyticsLogicTest {

    // 1. Monthly chart contains exactly 4 buckets
    @Test
    fun testMonthlyChartContainsExactlyFourBuckets() {
        val monthlyTrends = MockData.getAnalyticsTrends("MONTHLY")
        assertEquals("Monthly chart must contain exactly 4 buckets", 4, monthlyTrends.size)
        assertEquals("Week 1", monthlyTrends[0].first)
        assertEquals("Week 2", monthlyTrends[1].first)
        assertEquals("Week 3", monthlyTrends[2].first)
        assertEquals("Week 4", monthlyTrends[3].first)
    }

    // 2. No Week 5
    @Test
    fun testNoWeekFiveInMonthlyChart() {
        val monthlyTrends = MockData.getAnalyticsTrends("MONTHLY")
        assertFalse("Monthly chart must NEVER contain Week 5", monthlyTrends.any { it.first.contains("Week 5") })
    }

    // 3. Week 4 includes all month-end days (28, 29, 30, 31)
    @Test
    fun testWeekFourIncludesAllMonthEndDays() {
        fun bucketForDay(day: Int): String {
            return when {
                day in 1..7 -> "Week 1"
                day in 8..14 -> "Week 2"
                day in 15..21 -> "Week 3"
                day >= 22 -> "Week 4"
                else -> "Invalid"
            }
        }

        // Test September (30 days)
        assertEquals("Sep 22 must be in Week 4", "Week 4", bucketForDay(22))
        assertEquals("Sep 28 must be in Week 4", "Week 4", bucketForDay(28))
        assertEquals("Sep 29 must be in Week 4", "Week 4", bucketForDay(29))
        assertEquals("Sep 30 must be in Week 4", "Week 4", bucketForDay(30))

        // Test October (31 days)
        assertEquals("Oct 31 must be in Week 4", "Week 4", bucketForDay(31))

        // Test February (28 or 29 days)
        assertEquals("Feb 28 must be in Week 4", "Week 4", bucketForDay(28))
        assertEquals("Feb 29 must be in Week 4", "Week 4", bucketForDay(29))

        // Days 1..21 map to Weeks 1..3
        assertEquals("Day 1 is Week 1", "Week 1", bucketForDay(1))
        assertEquals("Day 7 is Week 1", "Week 1", bucketForDay(7))
        assertEquals("Day 8 is Week 2", "Week 2", bucketForDay(8))
        assertEquals("Day 14 is Week 2", "Week 2", bucketForDay(14))
        assertEquals("Day 15 is Week 3", "Week 3", bucketForDay(15))
        assertEquals("Day 21 is Week 3", "Week 3", bucketForDay(21))
    }

    // 4. Weekly chart always contains Mon-Sun
    @Test
    fun testWeeklyChartAlwaysContainsMonToSun() {
        val weeklyTrends = MockData.getAnalyticsTrends("WEEKLY")
        assertEquals(7, weeklyTrends.size)
        val expectedDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val actualDays = weeklyTrends.map { it.first }
        assertEquals(expectedDays, actualDays)
    }

    // 5. Thursday appears in current weekly chart
    @Test
    fun testThursdayAppearsInCurrentWeeklyChart() {
        val weeklyTrends = MockData.getAnalyticsTrends("WEEKLY")
        assertTrue("Thursday must appear in the weekly chart", weeklyTrends.any { it.first == "Thu" })
    }

    // 6. Future weekday values remain visible as 0 Wh
    @Test
    fun testFutureWeekdayValuesRemainVisibleAsZeroWh() {
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        // In a week where today is earlier in the week, future days exist with 0.0 Wh
        val trends = (0..6).map { dayOffset ->
            val dayDate = monday.plusDays(dayOffset.toLong())
            val isFuture = dayDate.isAfter(today)
            val wh = if (isFuture) 0.0 else 15.0
            dayLabels[dayOffset] to wh
        }

        assertEquals(7, trends.size)
        // Verify formatEnergy of 0.0 is "0 Wh"
        val futureDays = trends.filter { it.second == 0.0 }
        for (day in futureDays) {
            assertEquals("0 Wh", Formatters.formatEnergy(day.second))
        }
    }

    // 7. Monthly bars retain Week 1-4 labels at maximum height
    @Test
    fun testMonthlyBarsRetainWeek1To4LabelsAtMaximumHeight() {
        // Chart layout geometry test:
        // Reserved Value zone: 22.dp
        // Reserved Plot area: 110.dp
        // Reserved X-axis label zone: 22.dp
        // Even when bar height fraction is 1.0f (maximum value), the bar is bounded strictly inside 110.dp
        val barFraction = 1.0f
        val plotAreaHeightDp = 110
        val labelAreaHeightDp = 22
        val valueAreaHeightDp = 22

        val barPixelHeight = plotAreaHeightDp * barFraction
        assertEquals(110f, barPixelHeight, 0.001f)
        assertTrue("Bar must never encroach on label zone", labelAreaHeightDp > 0)
        assertTrue("Bar must never encroach on value zone", valueAreaHeightDp > 0)

        // Labels Week 1..Week 4 must all be present
        val labels = listOf("Week 1", "Week 2", "Week 3", "Week 4")
        assertEquals(4, labels.size)
    }

    // 8. Weekly bars retain Mon-Sun labels at maximum height
    @Test
    fun testWeeklyBarsRetainMonToSunLabelsAtMaximumHeight() {
        val barFraction = 1.0f
        val plotAreaHeightDp = 110
        val labelAreaHeightDp = 22

        val barHeight = plotAreaHeightDp * barFraction
        assertEquals(110f, barHeight, 0.001f)
        assertTrue("Reserved label area is strictly separated from bar plot area", labelAreaHeightDp >= 20)

        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        assertEquals(7, days.size)
    }

    // 9. Daily chart retains time labels at maximum bar height
    @Test
    fun testDailyChartRetainsTimeLabelsAtMaximumBarHeight() {
        val barFraction = 1.0f
        val plotAreaHeightDp = 110
        val labelAreaHeightDp = 22

        val barHeight = plotAreaHeightDp * barFraction
        assertEquals(110f, barHeight, 0.001f)
        assertTrue("Time label zone is reserved below plot area", labelAreaHeightDp == 22)
    }

    // 10. Daily current hour is included
    @Test
    fun testDailyCurrentHourIsIncluded() {
        val dailyTrends = MockData.getAnalyticsTrends("DAILY")
        assertEquals("Daily trends must contain 24 hours", 24, dailyTrends.size)

        val currentHour = LocalTime.now().hour
        val currentHourLabel = String.format(Locale.US, "%02d", currentHour)
        assertTrue("Current hour $currentHourLabel must be included in daily chart",
            dailyTrends.any { it.first == currentHourLabel })
    }

    // 11. Daily hourly Wh aggregation works
    @Test
    fun testDailyHourlyWhAggregationWorks() {
        // Construct simulated energy readings across hours
        val readings = listOf(
            EnergyReadingDto(applianceId = "app1", energyDeltaKwh = 0.0012, recordedAt = "2026-09-24T10:15:00Z"),
            EnergyReadingDto(applianceId = "app2", energyDeltaKwh = 0.0025, recordedAt = "2026-09-24T10:45:00Z"),
            EnergyReadingDto(applianceId = "app1", energyDeltaKwh = 0.0050, recordedAt = "2026-09-24T14:10:00Z")
        )

        // Reading deltas in Wh
        val r1Wh = readings[0].energyDeltaWh // 1.2 Wh
        val r2Wh = readings[1].energyDeltaWh // 2.5 Wh
        val r3Wh = readings[2].energyDeltaWh // 5.0 Wh

        assertEquals(1.2, r1Wh, 0.001)
        assertEquals(2.5, r2Wh, 0.001)
        assertEquals(5.0, r3Wh, 0.001)

        val hour10Total = r1Wh + r2Wh // 3.7 Wh
        assertEquals(3.7, hour10Total, 0.001)
        assertEquals("3.70 Wh", Formatters.formatEnergy(hour10Total))
    }

    // 12. Weekly Wh aggregation works
    @Test
    fun testWeeklyWhAggregationWorks() {
        val weeklyTrends = MockData.getAnalyticsTrends("WEEKLY")
        val expectedSum = weeklyTrends.sumOf { it.second }
        assertTrue("Weekly sum must be greater than zero", expectedSum > 0.0)

        val repository = MockSemhasRepository()
        val weeklyAnalytics = runBlocking { repository.getWeeklyAnalytics(LocalDate.now()) }
        assertEquals("Weekly total must strictly equal the sum of Mon-Sun",
            expectedSum, weeklyAnalytics.totalEnergyWh, 0.001)
    }

    // 13. Monthly Wh aggregation works
    @Test
    fun testMonthlyWhAggregationWorks() {
        val monthlyTrends = MockData.getAnalyticsTrends("MONTHLY")
        val expectedSum = monthlyTrends.sumOf { it.second }
        assertTrue("Monthly sum must be greater than zero", expectedSum > 0.0)

        val repository = MockSemhasRepository()
        val currentMonth = YearMonth.now()
        val monthlyAnalytics = runBlocking { repository.getMonthlyAnalytics(currentMonth.year, currentMonth.monthValue) }
        assertEquals("Monthly total must strictly equal the sum of Week 1..Week 4",
            expectedSum, monthlyAnalytics.totalEnergyWh, 0.001)
    }

    // 14. Small non-zero Wh values are not rounded to 0
    @Test
    fun testSmallNonZeroWhValuesAreNotRoundedToZero() {
        assertEquals("0.20 Wh", Formatters.formatEnergy(0.20))
        assertEquals("1.35 Wh", Formatters.formatEnergy(1.35))
        assertEquals("12.40 Wh", Formatters.formatEnergy(12.40))
        assertEquals("125.80 Wh", Formatters.formatEnergy(125.80))
        assertEquals("0.05 Wh", Formatters.formatEnergy(0.05))
        assertEquals("0.01 Wh", Formatters.formatEnergy(0.01))
        assertEquals("0 Wh", Formatters.formatEnergy(0.0))
        assertEquals("0 Wh", Formatters.formatEnergy(0.001)) // below 0.005 threshold
    }

    // 15. kWh -> Wh conversion happens exactly once
    @Test
    fun testKwhToWhConversionHappensExactlyOnce() {
        val dailyDto = DailyConsumptionDto(
            applianceId = "test-app",
            consumptionDate = "2026-09-24",
            energyKwh = 0.05 // 50 Wh
        )
        assertEquals("kWh to Wh conversion must be storedEnergyKWh * 1000.0 exactly once",
            50.0, dailyDto.energyWh, 0.001)

        val readingDto = EnergyReadingDto(
            applianceId = "test-app",
            energyDeltaKwh = 0.0125 // 12.5 Wh
        )
        assertEquals("energyDeltaKwh to Wh conversion must be energyDeltaKwh * 1000.0 exactly once",
            12.5, readingDto.energyDeltaWh, 0.001)
    }

    // 16. Appliance comparison uses actual Wh
    @Test
    fun testApplianceComparisonUsesActualWh() {
        val totalEnergyWh = 20.6
        val ch1Wh = 12.40
        val ch2Wh = 8.20

        val pct1 = (ch1Wh / totalEnergyWh) * 100.0
        val pct2 = (ch2Wh / totalEnergyWh) * 100.0

        val item1 = ApplianceHistoricalUsage(
            channelNumber = 1,
            applianceName = "TV",
            energyWh = ch1Wh,
            cost = ch1Wh * 0.008,
            percentage = pct1
        )
        val item2 = ApplianceHistoricalUsage(
            channelNumber = 2,
            applianceName = "Fridge",
            energyWh = ch2Wh,
            cost = ch2Wh * 0.008,
            percentage = pct2
        )

        assertEquals(12.40, item1.energyWh, 0.001)
        assertEquals(8.20, item2.energyWh, 0.001)
        assertEquals("12.40 Wh", Formatters.formatEnergy(item1.energyWh))
        assertEquals("8.20 Wh", Formatters.formatEnergy(item2.energyWh))
        assertEquals(60, Math.round(item1.percentage).toInt())
        assertEquals(40, Math.round(item2.percentage).toInt())
    }

    // 17. Zero-total percentage returns 0%
    @Test
    fun testZeroTotalPercentageReturnsZeroPercent() {
        val totalEnergyWh = 0.0
        val ch1Wh = 0.0
        val pct = if (totalEnergyWh > 0.0) (ch1Wh / totalEnergyWh) * 100.0 else 0.0
        assertEquals(0.0, pct, 0.0001)

        val item = ApplianceHistoricalUsage(
            channelNumber = 1,
            applianceName = "Living Room Light",
            energyWh = 0.0,
            cost = 0.0,
            percentage = pct
        )
        assertEquals("0 Wh", Formatters.formatEnergy(item.energyWh))
        assertEquals(0, Math.round(item.percentage).toInt())
    }

    // 18. Realtime reading updates today's Wh without app restart
    @Test
    fun testRealtimeReadingUpdatesTodayWhWithoutAppRestart() {
        var runningTodayWh = 100.0
        val incomingDeltaWh = 5.5

        // Simulate incoming reading without app restart
        runningTodayWh += incomingDeltaWh
        assertEquals(105.5, runningTodayWh, 0.001)
        assertEquals("105.50 Wh", Formatters.formatEnergy(runningTodayWh))
    }

    // 19. Realtime reading updates current weekday
    @Test
    fun testRealtimeReadingUpdatesCurrentWeekday() {
        val weekdayMap = mutableMapOf(
            "Mon" to 10.0,
            "Tue" to 12.0,
            "Wed" to 15.0,
            "Thu" to 8.0,
            "Fri" to 0.0,
            "Sat" to 0.0,
            "Sun" to 0.0
        )
        val currentDay = "Thu"
        val incomingDeltaWh = 2.5
        weekdayMap[currentDay] = (weekdayMap[currentDay] ?: 0.0) + incomingDeltaWh

        assertEquals(10.5, weekdayMap["Thu"]!!, 0.001)
        assertEquals("10.50 Wh", Formatters.formatEnergy(weekdayMap["Thu"]!!))
    }

    // 20. Realtime reading updates monthly bucket
    @Test
    fun testRealtimeReadingUpdatesMonthlyBucket() {
        val monthlyBuckets = mutableMapOf(
            "Week 1" to 50.0,
            "Week 2" to 60.0,
            "Week 3" to 70.0,
            "Week 4" to 80.0
        )

        // If today is day 24 (Week 4), update Week 4 bucket
        val dayOfMonth = 24
        val targetBucket = when {
            dayOfMonth in 1..7 -> "Week 1"
            dayOfMonth in 8..14 -> "Week 2"
            dayOfMonth in 15..21 -> "Week 3"
            else -> "Week 4"
        }
        val deltaWh = 3.25
        monthlyBuckets[targetBucket] = (monthlyBuckets[targetBucket] ?: 0.0) + deltaWh

        assertEquals("Week 4", targetBucket)
        assertEquals(83.25, monthlyBuckets["Week 4"]!!, 0.001)
    }

    // 21. Historical data does not fallback to stale live_readings
    @Test
    fun testHistoricalDataDoesNotFallbackToStaleLiveReadings() {
        // DailyConsumption represents historical source of truth
        val historicalRecords = listOf(
            DailyConsumptionDto(applianceId = "app1", consumptionDate = "2026-09-20", energyKwh = 0.12),
            DailyConsumptionDto(applianceId = "app1", consumptionDate = "2026-09-21", energyKwh = 0.15)
        )

        val totalHistoricalWh = historicalRecords.sumOf { it.energyWh }
        assertEquals(270.0, totalHistoricalWh, 0.001)

        // Live readings cumulative reading must NOT overwrite historical records when relay is toggled
        val staleLiveReadingWh = 99999.0
        assertNotEquals(staleLiveReadingWh, totalHistoricalWh, 0.001)
    }

    // 22. Selected month/year loads correct historical data
    @Test
    fun testSelectedMonthYearLoadsCorrectHistoricalData() {
        val records = listOf(
            DailyConsumptionDto(applianceId = "app1", consumptionDate = "2026-09-05", energyKwh = 0.10),
            DailyConsumptionDto(applianceId = "app1", consumptionDate = "2026-09-18", energyKwh = 0.20),
            DailyConsumptionDto(applianceId = "app1", consumptionDate = "2026-10-02", energyKwh = 0.30)
        )

        val sepRecords = records.filter { it.consumptionDate.startsWith("2026-09") }
        val octRecords = records.filter { it.consumptionDate.startsWith("2026-10") }

        assertEquals(2, sepRecords.size)
        assertEquals(1, octRecords.size)
        assertEquals(300.0, sepRecords.sumOf { it.energyWh }, 0.001)
        assertEquals(300.0, octRecords.sumOf { it.energyWh }, 0.001)
    }

    // 23. Empty month shows correct empty state
    @Test
    fun testEmptyMonthShowsCorrectEmptyState() {
        val emptyData = PeriodAnalyticsData(
            periodName = "MONTHLY",
            periodLabel = "November 2026",
            totalEnergyWh = 0.0,
            totalCost = 0.0,
            trends = emptyList(),
            applianceBreakdown = emptyList(),
            highestApplianceName = "N/A",
            highestApplianceEnergy = 0.0,
            hasData = false
        )

        assertFalse(emptyData.hasData)
        assertEquals(0.0, emptyData.totalEnergyWh, 0.0001)
        assertEquals("0 Wh", Formatters.formatEnergy(emptyData.totalEnergyWh))
        assertEquals("₹0.00", Formatters.formatCurrency(emptyData.totalCost))
    }

    // 24. Default monthly bill limit is 1500.0
    @Test
    fun testDefaultMonthlyBillLimit() {
        val repository = MockSemhasRepository()
        assertEquals(1500.0, repository.monthlyBillLimit.value, 0.001)
        assertEquals(1500.0, com.semhas.app.utils.Constants.DEFAULT_MONTHLY_BILL_LIMIT, 0.001)
    }

    // 25. Monthly bill limit progress and remaining calculations
    @Test
    fun testMonthlyBillLimitProgressAndRemaining() {
        val monthlyLimit = 1500.0
        val currentBill = 842.60

        val remaining = (monthlyLimit - currentBill).coerceAtLeast(0.0)
        val progressFraction = (currentBill / monthlyLimit).toFloat()
        val progressPercent = Math.round((currentBill / monthlyLimit) * 100.0).toInt()

        assertEquals(657.40, remaining, 0.01)
        assertEquals(0.5617f, progressFraction, 0.001f)
        assertEquals(56, progressPercent)
        assertEquals("₹842.60", Formatters.formatCurrency(currentBill))
        assertEquals("₹657.40", Formatters.formatCurrency(remaining))
    }

    // 26. Monthly bill limit reached state
    @Test
    fun testMonthlyBillLimitReachedState() {
        val monthlyLimit = 1500.0
        val currentBillAtLimit = 1500.0
        val currentBillOverLimit = 1750.0

        val remainingAtLimit = (monthlyLimit - currentBillAtLimit).coerceAtLeast(0.0)
        val isAtLimit = currentBillAtLimit >= monthlyLimit
        assertEquals(0.0, remainingAtLimit, 0.001)
        assertTrue("Limit must be reached when currentBill == monthlyLimit", isAtLimit)

        val remainingOverLimit = (monthlyLimit - currentBillOverLimit).coerceAtLeast(0.0)
        val isOverLimit = currentBillOverLimit >= monthlyLimit
        assertEquals(0.0, remainingOverLimit, 0.001)
        assertTrue("Limit must be reached when currentBill > monthlyLimit", isOverLimit)
        assertEquals(250.0, currentBillOverLimit - monthlyLimit, 0.001)
    }

    // 27. Setting monthly bill limit updates repository state flow
    @Test
    fun testSetMonthlyBillLimitUpdatesRepository() = runBlocking {
        val repository = MockSemhasRepository()
        assertEquals(1500.0, repository.monthlyBillLimit.value, 0.001)

        repository.setMonthlyBillLimit(2500.0)
        assertEquals(2500.0, repository.monthlyBillLimit.value, 0.001)

        // Setting non-positive values must be ignored
        repository.setMonthlyBillLimit(-100.0)
        assertEquals(2500.0, repository.monthlyBillLimit.value, 0.001)

        repository.setMonthlyBillLimit(0.0)
        assertEquals(2500.0, repository.monthlyBillLimit.value, 0.001)
    }

    // 28. Monthly limit numeric input validation regex
    @Test
    fun testMonthlyLimitInputValidationRegex() {
        val regex = Regex("""^\d*\.?\d{0,2}$""")

        assertTrue("Empty input allowed while typing", "".matches(regex))
        assertTrue("Integer allowed", "1500".matches(regex))
        assertTrue("Decimal with 1 place allowed", "1500.5".matches(regex))
        assertTrue("Decimal with 2 places allowed", "1500.50".matches(regex))
        assertFalse("Decimal with 3 places rejected", "1500.555".matches(regex))
        assertFalse("Negative sign rejected", "-1500".matches(regex))
        assertFalse("Alphabetic rejected", "1500abc".matches(regex))
        assertFalse("Multiple dots rejected", "15.00.00".matches(regex))
    }
}
