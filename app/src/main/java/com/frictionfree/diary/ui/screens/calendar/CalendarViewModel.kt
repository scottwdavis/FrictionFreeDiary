package com.frictionfree.diary.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.repository.DiaryRepository
import com.frictionfree.diary.utils.DateFormatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

enum class CalendarViewMode {
    MONTH,
    WEEK
}

data class CalendarUiState(
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val currentYear: Int = 2026,
    val currentMonth: Int = 8, // 0-indexed (8 = September)
    val selectedDateMillis: Long = System.currentTimeMillis(),
    val entriesInMonth: List<DiaryEntry> = emptyList(),
    val weekEntries: List<DiaryEntry> = emptyList(),
    val selectedDayEntries: List<DiaryEntry> = emptyList()
)

class CalendarViewModel(
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val calendar = Calendar.getInstance()

    private val _viewMode = MutableStateFlow(CalendarViewMode.MONTH)
    private val _currentYear = MutableStateFlow(calendar.get(Calendar.YEAR))
    private val _currentMonth = MutableStateFlow(calendar.get(Calendar.MONTH))
    private val _selectedDate = MutableStateFlow(System.currentTimeMillis())

    val uiState: StateFlow<CalendarUiState> = combine(
        _viewMode,
        _currentYear,
        _currentMonth,
        _selectedDate
    ) { mode, year, month, selectedDate ->
        CalendarFilter(mode, year, month, selectedDate)
    }.flatMapLatest { filter ->
        val (monthStart, monthEnd) = getMonthRange(filter.year, filter.month)
        val (weekStart, weekEnd) = getWeekRange(filter.selectedDate)

        val rangeStart = minOf(monthStart, weekStart)
        val rangeEnd = maxOf(monthEnd, weekEnd)

        diaryRepository.getEntriesInRange(rangeStart, rangeEnd).map { allEntries ->
            val dayStart = DateFormatters.getStartOfDay(filter.selectedDate)
            val dayEnd = DateFormatters.getEndOfDay(filter.selectedDate)

            val monthEntries = allEntries.filter { it.createdAt in monthStart..monthEnd }
            val weekEntries = allEntries.filter { it.createdAt in weekStart..weekEnd }
            val dayEntries = allEntries.filter { it.createdAt in dayStart..dayEnd }

            CalendarUiState(
                viewMode = filter.mode,
                currentYear = filter.year,
                currentMonth = filter.month,
                selectedDateMillis = filter.selectedDate,
                entriesInMonth = monthEntries,
                weekEntries = weekEntries,
                selectedDayEntries = dayEntries
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, CalendarUiState())

    private data class CalendarFilter(
        val mode: CalendarViewMode,
        val year: Int,
        val month: Int,
        val selectedDate: Long
    )

    fun setViewMode(mode: CalendarViewMode) {
        _viewMode.value = mode
    }

    fun selectDate(timestamp: Long, switchToWeek: Boolean = false) {
        _selectedDate.value = timestamp
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        _currentYear.value = cal.get(Calendar.YEAR)
        _currentMonth.value = cal.get(Calendar.MONTH)
        if (switchToWeek) {
            _viewMode.value = CalendarViewMode.WEEK
        }
    }

    fun setMonth(year: Int, month: Int) {
        _currentYear.value = year
        _currentMonth.value = month
    }

    fun changeWeek(deltaWeeks: Int) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = _selectedDate.value
            add(Calendar.WEEK_OF_YEAR, deltaWeeks)
        }
        _selectedDate.value = cal.timeInMillis
        _currentYear.value = cal.get(Calendar.YEAR)
        _currentMonth.value = cal.get(Calendar.MONTH)
    }

    private fun getMonthRange(year: Int, month: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, maxDay)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return Pair(start, end)
    }

    private fun getWeekRange(timestamp: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_WEEK, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return Pair(start, end)
    }
}
