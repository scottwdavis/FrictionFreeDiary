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
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class CalendarUiState(
    val currentYear: Int = 2026,
    val currentMonth: Int = 8, // 0-indexed (8 = September)
    val selectedDateMillis: Long = System.currentTimeMillis(),
    val entriesInMonth: List<DiaryEntry> = emptyList(),
    val selectedDayEntries: List<DiaryEntry> = emptyList()
)

class CalendarViewModel(
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val calendar = Calendar.getInstance()

    private val _currentYear = MutableStateFlow(calendar.get(Calendar.YEAR))
    private val _currentMonth = MutableStateFlow(calendar.get(Calendar.MONTH))
    private val _selectedDate = MutableStateFlow(System.currentTimeMillis())

    val uiState: StateFlow<CalendarUiState> = combine(
        _currentYear,
        _currentMonth,
        _selectedDate
    ) { year, month, selectedDate ->
        Triple(year, month, selectedDate)
    }.flatMapLatest { (year, month, selectedDate) ->
        val (monthStart, monthEnd) = getMonthRange(year, month)
        diaryRepository.getEntriesInRange(monthStart, monthEnd).combine(MutableStateFlow(selectedDate)) { entries, selDate ->
            val dayStart = DateFormatters.getStartOfDay(selDate)
            val dayEnd = DateFormatters.getEndOfDay(selDate)
            val dayEntries = entries.filter { it.createdAt in dayStart..dayEnd }

            CalendarUiState(
                currentYear = year,
                currentMonth = month,
                selectedDateMillis = selDate,
                entriesInMonth = entries,
                selectedDayEntries = dayEntries
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, CalendarUiState())

    fun selectDate(timestamp: Long) {
        _selectedDate.value = timestamp
    }

    fun changeMonth(delta: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, _currentYear.value)
            set(Calendar.MONTH, _currentMonth.value)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, delta)
        }
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
}
