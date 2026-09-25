package com.semhas.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semhas.app.data.model.Channel
import com.semhas.app.data.model.HistoricalBillingSummary
import com.semhas.app.data.model.PeriodAnalyticsData
import com.semhas.app.data.repository.SemhasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class AnalyticsPeriod {
    DAILY,
    WEEKLY,
    MONTHLY
}

data class AnalyticsUiState(
    val selectedPeriod: AnalyticsPeriod = AnalyticsPeriod.DAILY,
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val availableMonths: List<YearMonth> = emptyList(),
    val periodData: PeriodAnalyticsData = PeriodAnalyticsData(),
    val billingSummary: HistoricalBillingSummary = HistoricalBillingSummary(),
    val channels: List<Channel> = emptyList(),
    val isLoading: Boolean = false
)

class AnalyticsViewModel(
    private val repository: SemhasRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(AnalyticsPeriod.DAILY)
    val selectedPeriod: StateFlow<AnalyticsPeriod> = _selectedPeriod.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _periodData = MutableStateFlow(PeriodAnalyticsData())
    private val _isLoading = MutableStateFlow(false)

    // Generate list of available months (last 6 months through next month)
    private val availableMonths: List<YearMonth> = run {
        val current = YearMonth.now()
        (-5..1).map { offset -> current.plusMonths(offset.toLong()) }
    }

    private data class PeriodSelection(
        val period: AnalyticsPeriod,
        val date: LocalDate,
        val month: YearMonth,
        val loading: Boolean
    )

    private val selectionFlow = combine(
        _selectedPeriod,
        _selectedDate,
        _selectedMonth,
        _isLoading
    ) { period, date, month, loading ->
        PeriodSelection(period, date, month, loading)
    }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        selectionFlow,
        _periodData,
        repository.billingSummary,
        repository.channels
    ) { sel, periodData, billingSummary, channels ->
        AnalyticsUiState(
            selectedPeriod = sel.period,
            selectedDate = sel.date,
            selectedMonth = sel.month,
            availableMonths = availableMonths,
            periodData = periodData,
            billingSummary = billingSummary,
            channels = channels,
            isLoading = sel.loading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState(isLoading = true, availableMonths = availableMonths)
    )

    init {
        // Initial load and observe repository data changes
        viewModelScope.launch {
            repository.billingSummary.collect {
                loadCurrentPeriodData()
            }
        }
        viewModelScope.launch {
            repository.channels.collect {
                loadCurrentPeriodData()
            }
        }
        loadCurrentPeriodData()
    }

    fun selectPeriod(period: AnalyticsPeriod) {
        if (_selectedPeriod.value != period) {
            _selectedPeriod.value = period
            loadCurrentPeriodData()
        }
    }

    fun selectMonth(yearMonth: YearMonth) {
        if (_selectedMonth.value != yearMonth) {
            _selectedMonth.value = yearMonth
            loadCurrentPeriodData()
        }
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
        loadCurrentPeriodData()
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
        loadCurrentPeriodData()
    }

    fun selectDate(date: LocalDate) {
        if (_selectedDate.value != date) {
            _selectedDate.value = date
            loadCurrentPeriodData()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.refreshHistoricalAnalytics()
            } finally {
                loadCurrentPeriodData()
                _isLoading.value = false
            }
        }
    }

    private fun loadCurrentPeriodData() {
        viewModelScope.launch {
            val period = _selectedPeriod.value
            val data = when (period) {
                AnalyticsPeriod.DAILY -> repository.getDailyAnalytics(_selectedDate.value)
                AnalyticsPeriod.WEEKLY -> repository.getWeeklyAnalytics(_selectedDate.value)
                AnalyticsPeriod.MONTHLY -> {
                    val ym = _selectedMonth.value
                    repository.getMonthlyAnalytics(ym.year, ym.monthValue)
                }
            }
            _periodData.value = data
        }
    }
}

