package com.patrickreynolds.steady.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.patrickreynolds.steady.analysis.DayRecord
import com.patrickreynolds.steady.analysis.Metric
import com.patrickreynolds.steady.analysis.Patterns
import com.patrickreynolds.steady.analysis.Rating
import com.patrickreynolds.steady.data.Store
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.exp

@Composable
fun TrendsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val version by store.version.collectAsState()
    val today = LocalDate.now()
    val all = remember(version, today) { store.days(today) }
    var range by rememberSaveable { mutableIntStateOf(30) }
    val shown = all.filter { it.date > today.minusDays(range.toLong()) }
    val first = today.minusDays(range.toLong() - 1)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Trends", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Done") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(14, 30, 90).forEach { d ->
                FilterChip(selected = range == d, onClick = { range = d }, label = { Text("$d days") })
            }
        }
        Legend()

        val hrvBase = Patterns.currentBaseline(all, Metric.HRV, today)
        TrendPanel(
            title = "HRV", unit = "ms", days = shown, first = first, count = range,
            value = { it.rmssdMs },
            band = hrvBase?.let { exp(it.low) to exp(it.high) },
            note = "Higher is usually better. Your own normal matters, not other people's.",
        )
        val rhrBase = Patterns.currentBaseline(all, Metric.RESTING_HR, today)
        TrendPanel(
            title = "Resting heart rate", unit = "bpm", days = shown, first = first, count = range,
            value = { it.restingHr }, band = rhrBase?.let { it.low to it.high },
            note = "A few beats above your normal can mean strain, a cold, or a poor night.",
        )
        val riseBase = Patterns.currentBaseline(all, Metric.RISE, today)
        TrendPanel(
            title = "Rise on standing", unit = "bpm", days = shown, first = first, count = range,
            value = { it.rise }, band = riseBase?.let { it.low to it.high },
            note = "How much your heart rate goes up in the minute after you stand.",
        )
        val stepBase = Patterns.currentBaseline(all, Metric.STEPS, today)
        TrendPanel(
            title = "Steps", unit = "", days = shown, first = first, count = range,
            value = { it.steps?.toDouble() }, band = stepBase?.let { it.low.coerceAtLeast(0.0) to it.high },
            note = "From your phone, so steps without it in your pocket are missed.",
        )
    }
}

@Composable
private fun Legend() {
    val s = LocalStateColors.current
    @Composable fun item(color: Color, text: String) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(14.dp).padding(1.dp)) { Canvas(Modifier.fillMaxSize()) { drawRect(color) } }
        Text(text, style = MaterialTheme.typography.bodySmall, color = s.muted)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        item(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), "Your normal")
        item(s.stop.copy(alpha = 0.35f), "Crash day")
        item(s.warn.copy(alpha = 0.3f), "Bad day")
    }
}

@Composable
private fun TrendPanel(
    title: String, unit: String, days: List<DayRecord>, first: LocalDate, count: Int,
    value: (DayRecord) -> Double?, band: Pair<Double, Double>?, note: String,
) {
    val s = LocalStateColors.current
    val points = days.mapNotNull { d -> value(d)?.let { d.date to it } }
    val latest = points.lastOrNull()
    val u = if (unit.isEmpty()) "" else " $unit"
    Panel {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            buildString {
                append(if (latest != null) "Latest: ${fmt(latest.second)}$u" else "No data yet")
                append(if (band != null) "\nYour normal: ${fmt(band.first)}–${fmt(band.second)}$u" else "\nYour normal shows after ${Patterns.MIN_BASELINE_VALUES} days")
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (points.isNotEmpty()) {
            Chart(points, days, first, count, band)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val f = DateTimeFormatter.ofPattern("d MMM")
                Text(first.format(f), style = MaterialTheme.typography.bodySmall, color = s.muted)
                Text("Today", style = MaterialTheme.typography.bodySmall, color = s.muted)
            }
        }
        Text(note, style = MaterialTheme.typography.bodySmall, color = s.muted)
    }
}

@Composable
private fun Chart(points: List<Pair<LocalDate, Double>>, days: List<DayRecord>, first: LocalDate, count: Int, band: Pair<Double, Double>?) {
    val s = LocalStateColors.current
    val line = MaterialTheme.colorScheme.primary
    val bandColor = line.copy(alpha = 0.16f)
    val grid = MaterialTheme.colorScheme.outline
    val crashDays = days.filter { it.rating == Rating.CRASH }.map { it.date }
    val badDays = days.filter { it.rating == Rating.BAD }.map { it.date }
    val values = points.map { it.second } + listOfNotNull(band?.first, band?.second)
    val lo = values.min()
    val hi = values.max()
    val pad = ((hi - lo) * 0.15).coerceAtLeast(1e-3)
    val yMin = lo - pad
    val yMax = hi + pad

    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val slot = size.width / count
        fun x(d: LocalDate) = (java.time.temporal.ChronoUnit.DAYS.between(first, d) + 0.5f).toFloat() * slot
        fun y(v: Double) = (size.height * (1 - (v - yMin) / (yMax - yMin))).toFloat()

        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        crashDays.forEach { d -> drawRect(s.stop.copy(alpha = 0.22f), Offset(x(d) - slot / 2, 0f), Size(slot, size.height)) }
        badDays.forEach { d -> drawRect(s.warn.copy(alpha = 0.18f), Offset(x(d) - slot / 2, 0f), Size(slot, size.height)) }
        band?.let { (a, b) -> drawRect(bandColor, Offset(0f, y(b)), Size(size.width, y(a) - y(b))) }

        val path = Path()
        points.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(x(d), y(v)) else path.lineTo(x(d), y(v)) }
        drawPath(path, line.copy(alpha = 0.7f), style = Stroke(2.dp.toPx()))
        val r = (if (count > 40) 2.5f else 4f).dp.toPx()
        points.forEach { (d, v) -> drawCircle(line, r, Offset(x(d), y(v))) }
        points.lastOrNull()?.let { (d, v) -> drawCircle(line, r * 1.6f, Offset(x(d), y(v))) }
    }
}
