package com.patrickreynolds.steady.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrickreynolds.steady.analysis.Rating
import java.util.Locale

/** A plain bordered block. Used for the few things on screen that stand alone. */
@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surface, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.primary, content: Color = MaterialTheme.colorScheme.onPrimary) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** The four rating buttons, two by two, each coloured by what it means. */
@Composable
fun RatingButtons(onRate: (Rating) -> Unit) {
    val s = LocalStateColors.current
    val cs = MaterialTheme.colorScheme
    @Composable
    fun one(r: Rating, bg: Color, fg: Color, m: Modifier) = Button(
        onClick = { onRate(r) },
        modifier = m.heightIn(min = 58.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
    ) { Text(r.label, style = MaterialTheme.typography.labelLarge) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            one(Rating.GOOD, s.okSoft, s.ok, Modifier.weight(1f))
            one(Rating.OKAY, cs.surfaceVariant, cs.onSurface, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            one(Rating.BAD, s.warnSoft, s.warn, Modifier.weight(1f))
            one(Rating.CRASH, s.stopSoft, s.stop, Modifier.weight(1f))
        }
    }
}

fun fmt(v: Double?, digits: Int = 0): String = v?.let { "%.${digits}f".format(Locale.getDefault(), it) } ?: "–"
