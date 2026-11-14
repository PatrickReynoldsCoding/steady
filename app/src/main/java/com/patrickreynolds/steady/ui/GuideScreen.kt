package com.patrickreynolds.steady.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Shows GUIDE.md, bundled at build time. */
@Composable
fun GuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val blocks = remember {
        val text = runCatching { context.assets.open("GUIDE.md").bufferedReader().readText() }.getOrDefault("# Guide\n\nThe guide is missing from this build.")
        Markdown.parse(text)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onBack) { Text("Done") }
        }
        blocks.forEach { b ->
            when (b) {
                is Markdown.Block.Heading -> Text(
                    inline(b.text),
                    style = if (b.level == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = if (b.level == 1) 0.dp else 14.dp),
                )
                is Markdown.Block.Paragraph -> Text(inline(b.text), style = MaterialTheme.typography.bodyLarge)
                is Markdown.Block.Item -> Row(Modifier.padding(start = (b.indent * 22).dp), verticalAlignment = Alignment.Top) {
                    Text(b.marker, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(30.dp))
                    Text(inline(b.text), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        TextButton(onClick = onBack) { Text("Back to Steady") }
    }
}

/** **bold** and `code`, the only inline marks the guide uses. */
@Composable
private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    val re = Regex("""\*\*(.+?)\*\*|`(.+?)`""")
    var i = 0
    re.findAll(s).forEach { m ->
        append(s.substring(i, m.range.first))
        if (m.groupValues[1].isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        else withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(m.groupValues[2]) }
        i = m.range.last + 1
    }
    append(s.substring(i))
}

/** A very small Markdown reader: headings, paragraphs, bulleted and numbered lists. */
object Markdown {
    sealed interface Block {
        data class Heading(val level: Int, val text: String) : Block
        data class Paragraph(val text: String) : Block
        data class Item(val marker: String, val text: String, val indent: Int) : Block
    }

    fun parse(md: String): List<Block> {
        val out = mutableListOf<Block>()
        val para = StringBuilder()
        fun flush() { if (para.isNotBlank()) out += Block.Paragraph(para.toString().trim()); para.clear() }
        var last: Block.Item? = null
        for (raw in md.lines()) {
            val indent = raw.length - raw.trimStart().length
            val line = raw.trim()
            val heading = Regex("^(#{1,3}) (.*)").find(line)
            val bullet = Regex("^[-*] (.*)").find(line)
            val number = Regex("^(\\d+)\\. (.*)").find(line)
            when {
                line.isEmpty() -> { flush(); last = null }
                heading != null -> { flush(); last = null; out += Block.Heading(heading.groupValues[1].length, heading.groupValues[2]) }
                bullet != null -> { flush(); last = Block.Item("•", bullet.groupValues[1], indent / 2).also { out += it } }
                number != null -> { flush(); last = Block.Item("${number.groupValues[1]}.", number.groupValues[2], indent / 2).also { out += it } }
                last != null && indent > 0 -> {
                    // A continuation line under a list item: a new line inside the item.
                    val joined = last!!.copy(text = last!!.text + "\n" + line)
                    out[out.lastIndex] = joined; last = joined
                }
                else -> { last = null; if (para.isNotEmpty()) para.append(' '); para.append(line) }
            }
        }
        flush()
        return out
    }
}
