package com.frictionfree.diary.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.repository.DiaryRepository
import com.frictionfree.diary.utils.DateFormatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val allEntries: List<DiaryEntry> = emptyList(),
    val entryDayCounts: Map<String, Int> = emptyMap(), // "yyyy-M-d" -> count
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
        _selectedDate,
        diaryRepository.getAllEntries(isArchived = false)
    ) { mode, year, month, selectedDate, allEntries ->
        val (weekStart, weekEnd) = getWeekRange(selectedDate)
        val dayStart = DateFormatters.getStartOfDay(selectedDate)
        val dayEnd = DateFormatters.getEndOfDay(selectedDate)

        val cal = Calendar.getInstance()
        val entryCounts = mutableMapOf<String, Int>()
        allEntries.forEach { entry ->
            cal.timeInMillis = entry.createdAt
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            val d = cal.get(Calendar.DAY_OF_MONTH)
            val key = "$y-$m-$d"
            entryCounts[key] = (entryCounts[key] ?: 0) + 1
        }

        val weekEntries = allEntries.filter { it.createdAt in weekStart..weekEnd }
        val dayEntries = allEntries.filter { it.createdAt in dayStart..dayEnd }

        CalendarUiState(
            viewMode = mode,
            currentYear = year,
            currentMonth = month,
            selectedDateMillis = selectedDate,
            allEntries = allEntries,
            entryDayCounts = entryCounts,
            weekEntries = weekEntries,
            selectedDayEntries = dayEntries
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, CalendarUiState())

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

