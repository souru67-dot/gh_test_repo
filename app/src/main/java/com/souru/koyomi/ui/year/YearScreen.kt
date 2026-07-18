package com.souru.koyomi.ui.year

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.R
import com.souru.koyomi.data.holiday.Holidays
import com.souru.koyomi.ui.theme.LocalCalendarColors
import com.souru.koyomi.util.JapaneseEraFormat
import com.souru.koyomi.util.monthGridDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 年表示: the whole year as 12 mini months (3 per row). Tapping a month
 * jumps back to the month view at that month.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearScreen(
    onBack: () -> Unit,
    onOpenMonth: (LocalDate) -> Unit,
    useJapaneseEra: Boolean = false,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as KoyomiApplication }
    val weekStart by app.container.settingsRepository.weekStart
        .collectAsStateWithLifecycle(initialValue = DayOfWeek.SUNDAY)

    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    val yearLabel = if (useJapaneseEra) {
        "${year}年(${JapaneseEraFormat.yearLabel(year)})"
    } else {
        "${year}年"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (Locale.getDefault().language == "ja") yearLabel else year.toString())
                        IconButton(onClick = { year-- }) {
                            Icon(
                                Icons.Filled.ChevronLeft,
                                contentDescription = stringResource(R.string.previous_period),
                            )
                        }
                        IconButton(onClick = { year++ }) {
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = stringResource(R.string.next_period),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            for (rowStart in 1..12 step 3) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (monthValue in rowStart until rowStart + 3) {
                        MiniMonth(
                            month = YearMonth.of(year, monthValue),
                            weekStart = weekStart,
                            onClick = { onOpenMonth(LocalDate.of(year, monthValue, 1)) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMonth(
    month: YearMonth,
    weekStart: DayOfWeek,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    val calendarColors = LocalCalendarColors.current
    val days = remember(month, weekStart) { monthGridDays(month, weekStart) }
    val label = if (locale.language == "ja") {
        "${month.monthValue}月"
    } else {
        month.format(DateTimeFormatter.ofPattern("MMM", locale))
    }
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (month == YearMonth.from(today)) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.padding(bottom = 2.dp),
        )
        for (week in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val date = days[week * 7 + i]
                    val inMonth = YearMonth.from(date) == month
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (inMonth) {
                            val isToday = date == today
                            Box(
                                modifier = if (isToday) {
                                    Modifier
                                        .size(14.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier.size(14.dp)
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    fontSize = 8.sp,
                                    lineHeight = 9.sp,
                                    textAlign = TextAlign.Center,
                                    color = when {
                                        isToday -> MaterialTheme.colorScheme.onPrimary
                                        Holidays.isRedDay(date) -> calendarColors.sunday
                                        date.dayOfWeek == DayOfWeek.SATURDAY -> calendarColors.saturday
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        } else {
                            Box(modifier = Modifier.size(14.dp)) {}
                        }
                    }
                }
            }
        }
    }
}
