package com.frictionfree.diary.ui.screens.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.ui.components.EntryCard
import com.frictionfree.diary.utils.DateFormatters
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.util.Calendar

private const val BASE_YEAR = 1970
private const val TOTAL_MONTHS_COUNT = 2400 // 1970 to 2170

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onNavigateToEditor: (String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showPinpointDialog by remember { mutableStateOf(false) }

    // Calculate initial page index for HorizontalPager
    val initialPage = remember {
        val nowCal = Calendar.getInstance()
        val y = nowCal.get(Calendar.YEAR)
        val m = nowCal.get(Calendar.MONTH)
        (y - BASE_YEAR) * 12 + m
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { TOTAL_MONTHS_COUNT }
    )

    // Sync pager position with ViewModel when user swipes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val pageYear = BASE_YEAR + page / 12
            val pageMonth = page % 12
            if (pageYear != uiState.currentYear || pageMonth != uiState.currentMonth) {
                viewModel.setMonth(pageYear, pageMonth)
            }
        }
    }

    val monthNames = remember { DateFormatSymbols().months }
    val currentMonthName = monthNames[uiState.currentMonth]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.viewMode == CalendarViewMode.MONTH) {
                        // Tappable header with dropdown arrow to pinpoint any month/year
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showPinpointDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$currentMonthName ${uiState.currentYear}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Pinpoint Month & Year",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        // Week View Header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Week View",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (uiState.viewMode == CalendarViewMode.WEEK) {
                        IconButton(onClick = { viewModel.setViewMode(CalendarViewMode.MONTH) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Return to Month View")
                        }
                    }
                },
                actions = {
                    if (uiState.viewMode == CalendarViewMode.MONTH) {
                        // Jump to today button
                        IconButton(
                            onClick = {
                                val now = Calendar.getInstance()
                                val nowYear = now.get(Calendar.YEAR)
                                val nowMonth = now.get(Calendar.MONTH)
                                val targetPage = (nowYear - BASE_YEAR) * 12 + nowMonth
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                                viewModel.selectDate(now.timeInMillis, switchToWeek = false)
                            }
                        ) {
                            Icon(Icons.Default.Today, contentDescription = "Jump to Today")
                        }
                    } else {
                        // Switch back to Month view button
                        IconButton(onClick = { viewModel.setViewMode(CalendarViewMode.MONTH) }) {
                            Icon(Icons.Default.CalendarViewMonth, contentDescription = "Month Grid")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEditor(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Edit, contentDescription = "New Entry")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.viewMode) {
                CalendarViewMode.MONTH -> {
                    // Full-screen horizontal paging between months
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val pageYear = BASE_YEAR + page / 12
                        val pageMonth = page % 12

                        FullScreenMonthCalendar(
                            year = pageYear,
                            month = pageMonth,
                            selectedDateMillis = uiState.selectedDateMillis,
                            entriesInMonth = if (pageYear == uiState.currentYear && pageMonth == uiState.currentMonth) {
                                uiState.entriesInMonth
                            } else {
                                emptyList()
                            },
                            onDayClicked = { dayMillis ->
                                viewModel.selectDate(dayMillis, switchToWeek = true)
                            }
                        )
                    }
                }
                CalendarViewMode.WEEK -> {
                    // Centered Week View
                    CenteredWeekView(
                        selectedDateMillis = uiState.selectedDateMillis,
                        weekEntries = uiState.weekEntries,
                        selectedDayEntries = uiState.selectedDayEntries,
                        onDateSelected = { viewModel.selectDate(it, switchToWeek = false) },
                        onPreviousWeek = { viewModel.changeWeek(-1) },
                        onNextWeek = { viewModel.changeWeek(1) },
                        onNavigateToEditor = onNavigateToEditor
                    )
                }
            }

            // Month & Year Pinpoint Dialog
            if (showPinpointDialog) {
                MonthYearPinpointDialog(
                    initialYear = uiState.currentYear,
                    initialMonth = uiState.currentMonth,
                    onDismiss = { showPinpointDialog = false },
                    onConfirm = { selectedYear, selectedMonth ->
                        showPinpointDialog = false
                        val targetPage = (selectedYear - BASE_YEAR) * 12 + selectedMonth
                        coroutineScope.launch {
                            pagerState.scrollToPage(targetPage)
                        }
                        viewModel.setMonth(selectedYear, selectedMonth)
                    }
                )
            }
        }
    }
}

/**
 * Full-screen month view with day headers and flexible row heights.
 */
@Composable
private fun FullScreenMonthCalendar(
    year: Int,
    month: Int,
    selectedDateMillis: Long,
    entriesInMonth: List<DiaryEntry>,
    onDayClicked: (Long) -> Unit
) {
    val cal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }

    val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeekIndex = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 0 = Mon, 6 = Sun
    val totalWeeks = (firstDayOfWeekIndex + maxDays + 6) / 7

    val todayCal = Calendar.getInstance()
    val isCurrentCalendarMonth = todayCal.get(Calendar.YEAR) == year && todayCal.get(Calendar.MONTH) == month
    val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

    // Pre-calculate entry counts per day for fast lookup
    val entryDayCounts = remember(entriesInMonth) {
        val map = mutableMapOf<Int, Int>()
        entriesInMonth.forEach { entry ->
            val eCal = Calendar.getInstance().apply { timeInMillis = entry.createdAt }
            if (eCal.get(Calendar.YEAR) == year && eCal.get(Calendar.MONTH) == month) {
                val d = eCal.get(Calendar.DAY_OF_MONTH)
                map[d] = (map[d] ?: 0) + 1
            }
        }
        map
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Days of week header (Mon - Sun)
        val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Full-screen week rows
        for (week in 0 until totalWeeks) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                for (dayCol in 0..6) {
                    val cellIndex = week * 7 + dayCol
                    val dayNumber = cellIndex - firstDayOfWeekIndex + 1

                    if (dayNumber in 1..maxDays) {
                        val isToday = isCurrentCalendarMonth && dayNumber == todayDay
                        val entryCount = entryDayCounts[dayNumber] ?: 0

                        val cellCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayNumber)
                            set(Calendar.HOUR_OF_DAY, 12)
                        }

                        FullScreenDayCell(
                            dayNumber = dayNumber,
                            isToday = isToday,
                            entryCount = entryCount,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(2.dp),
                            onClick = { onDayClicked(cellCal.timeInMillis) }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun FullScreenDayCell(
    dayNumber: Int,
    isToday: Boolean,
    entryCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)

    val containerColor = if (isToday) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else if (entryCount > 0) {
        MaterialTheme.colorScheme.surfaceColorAtElevation(1.5.dp)
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(0.5.dp)
    }

    val borderStroke = if (isToday) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else if (entryCount > 0) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else {
        null
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .then(if (borderStroke != null) Modifier.border(borderStroke, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            // Day number
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isToday) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$dayNumber",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday || entryCount > 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            // Entry indicator dots or count pill
            if (entryCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    val dots = entryCount.coerceAtMost(3)
                    for (i in 0 until dots) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 1.5.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    if (entryCount > 3) {
                        Text(
                            text = "+",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 1.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

/**
 * Centered Week View: 7-day horizontal strip with selected day highlighted in the center,
 * followed by that day's entries.
 */
@Composable
private fun CenteredWeekView(
    selectedDateMillis: Long,
    weekEntries: List<DiaryEntry>,
    selectedDayEntries: List<DiaryEntry>,
    onDateSelected: (Long) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onNavigateToEditor: (String?) -> Unit
) {
    val selectedCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }

    // Build the 7 days of the active week (Monday to Sunday)
    val weekDays = remember(selectedDateMillis) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = selectedDateMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 12)
        }
        val days = mutableListOf<Long>()
        for (i in 0..6) {
            days.add(cal.timeInMillis)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        days
    }

    val todayCal = Calendar.getInstance()
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Column(modifier = Modifier.fillMaxSize()) {
        // Week switcher top bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header row: Week of MMM d - MMM d, YYYY
                val weekStartFormatted = DateFormatters.formatShortDate(weekDays.first())
                val weekEndFormatted = DateFormatters.formatShortDate(weekDays.last())

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPreviousWeek) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Week")
                    }

                    Text(
                        text = "$weekStartFormatted – $weekEndFormatted",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(onClick = onNextWeek) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Week")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 7-day centered horizontal strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekDays.forEachIndexed { index, dayMillis ->
                        val dayCal = Calendar.getInstance().apply { timeInMillis = dayMillis }
                        val isSelected = dayCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                                dayCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)
                        val isToday = dayCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                dayCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

                        val dayEntryCount = weekEntries.count { entry ->
                            val eCal = Calendar.getInstance().apply { timeInMillis = entry.createdAt }
                            eCal.get(Calendar.YEAR) == dayCal.get(Calendar.YEAR) &&
                                    eCal.get(Calendar.DAY_OF_YEAR) == dayCal.get(Calendar.DAY_OF_YEAR)
                        }

                        WeekDayPill(
                            dayName = dayNames[index],
                            dayNumber = dayCal.get(Calendar.DAY_OF_MONTH),
                            isSelected = isSelected,
                            isToday = isToday,
                            hasEntries = dayEntryCount > 0,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp),
                            onClick = { onDateSelected(dayMillis) }
                        )
                    }
                }
            }
        }

        // Section header for selected day
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = DateFormatters.formatFullDate(selectedDateMillis),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${selectedDayEntries.size} entries",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Entries list for selected day
        if (selectedDayEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No entries for this date.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { onNavigateToEditor(null) }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Write Entry")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedDayEntries, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        onClick = { onNavigateToEditor(entry.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekDayPill(
    dayName: String,
    dayNumber: Int,
    isSelected: Boolean,
    isToday: Boolean,
    hasEntries: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "weekPillScale"
    )

    val shape = RoundedCornerShape(14.dp)

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else if (isToday) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else if (isToday) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val borderStroke = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else if (isToday) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(containerColor)
            .border(borderStroke, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayName,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$dayNumber",
                fontSize = 16.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Entry dot indicator
            if (hasEntries) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                )
            } else {
                Spacer(modifier = Modifier.size(5.dp))
            }
        }
    }
}

/**
 * Month & Year Pinpoint Dialog: 1-2 tap jump to any month and year (e.g. July 5 years ago).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthYearPinpointDialog(
    initialYear: Int,
    initialMonth: Int,
    onDismiss: () -> Unit,
    onConfirm: (year: Int, month: Int) -> Unit
) {
    var selectedYear by remember { mutableIntStateOf(initialYear) }
    var selectedMonth by remember { mutableIntStateOf(initialMonth) }

    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val monthNames = remember {
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Jump to Date",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Quick Jump Chips
                Text(
                    text = "Quick Jump",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = {
                            selectedYear = currentYear
                            selectedMonth = Calendar.getInstance().get(Calendar.MONTH)
                        },
                        label = { Text("Today") }
                    )
                    AssistChip(
                        onClick = {
                            selectedYear = currentYear - 1
                        },
                        label = { Text("1y ago") }
                    )
                    AssistChip(
                        onClick = {
                            selectedYear = currentYear - 5
                        },
                        label = { Text("5y ago") }
                    )
                    AssistChip(
                        onClick = {
                            selectedYear = currentYear - 10
                        },
                        label = { Text("10y ago") }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Year selector (Horizontal scrollable chip row)
                Text(
                    text = "Select Year: $selectedYear",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                val years = remember { (currentYear + 2 downTo currentYear - 20).toList() }
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = (years.indexOf(selectedYear) - 2).coerceAtLeast(0)
                )

                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(years) { y ->
                        FilterChip(
                            selected = y == selectedYear,
                            onClick = { selectedYear = y },
                            label = { Text("$y") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Month selector (4x3 grid)
                Text(
                    text = "Select Month",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    monthNames.forEachIndexed { idx, name ->
                        FilterChip(
                            selected = idx == selectedMonth,
                            onClick = { selectedMonth = idx },
                            label = {
                                Text(
                                    text = name,
                                    modifier = Modifier.width(42.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedYear, selectedMonth) }) {
                Text("Go to Date")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
