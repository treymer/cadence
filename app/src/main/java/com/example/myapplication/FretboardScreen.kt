package com.example.myapplication

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val FB_NOTE_NAMES    = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
private val FB_DISPLAY_NAMES = listOf("C","C#/Db","D","D#/Eb","E","F","F#/Gb","G","G#/Ab","A","A#/Bb","B")

// Standard tuning, top → bottom on diagram (high e to low E)
private val FB_OPEN_NOTES    = listOf(4, 11, 7, 2, 9, 4) // E4, B3, G3, D3, A2, E2
private val FB_STRING_LABELS = listOf("e", "B", "G", "D", "A", "E")

private val INLAY_SINGLE = listOf(3, 5, 7, 9) // single-dot fret positions within a 12-fret block

@Composable
fun FretboardScreen(modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { 12 })
    val scope = rememberCoroutineScope()
    val noteIndex = pagerState.currentPage

    Column(modifier = modifier.fillMaxSize()) {

        // ── Header ───────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Text(
                "Note Finder",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Select a note or swipe",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FB_NOTE_NAMES.forEachIndexed { i, _ ->
                    FilterChip(
                        selected = noteIndex == i,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(i) } },
                        label    = { Text(FB_DISPLAY_NAMES[i]) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
        }

        // ── Swipeable fretboard pages ─────────────────────────────────────────
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Frets 0 – 12",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.height(4.dp))
                FretboardDiagram(noteIndex = page, firstFret = 0, lastFret = 12)

                Spacer(Modifier.height(24.dp))
                Text(
                    "Frets 13 – 24  (same notes, one octave higher)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.height(4.dp))
                FretboardDiagram(noteIndex = page, firstFret = 13, lastFret = 24)

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FretboardDiagram(
    noteIndex: Int,
    firstFret: Int,
    lastFret: Int,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimary    = MaterialTheme.colorScheme.onPrimary
    val onSurface    = MaterialTheme.colorScheme.onSurface
    val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    val hasOpenString = firstFret == 0
    // Number of fret slots (not counting open string slot)
    // firstFret=0, lastFret=12 → 12 fret slots (frets 1-12) + 1 open = 13 total
    // firstFret=13, lastFret=24 → 12 fret slots = 12 total
    val numFretSlots = lastFret - (if (hasOpenString) 0 else firstFret - 1)
    val totalSlots   = if (hasOpenString) numFretSlots + 1 else numFretSlots

    // Convert actual fret number to slot index within this diagram
    fun fretToSlot(fret: Int) = if (hasOpenString) fret else fret - firstFret

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        val numStrings = 6
        val labelW    = 20.dp.toPx()
        val fretNumH  = 22.dp.toPx()
        val nutW      = if (hasOpenString) 6.dp.toPx() else 0f
        val dotRadius = 9.dp.toPx()

        val boardLeft   = labelW + 4.dp.toPx()
        val boardRight  = size.width
        val boardTop    = dotRadius + 4.dp.toPx()
        val boardBottom = size.height - fretNumH

        val slotW         = (boardRight - boardLeft - nutW) / totalSlots
        val stringSpacing = (boardBottom - boardTop) / (numStrings - 1)

        // x center of slot index s (0-based)
        fun slotX(s: Int) = boardLeft + nutW + slotW * (s + 0.5f)

        // ── Strings ──────────────────────────────────────────────────────────
        FB_STRING_LABELS.forEachIndexed { si, label ->
            val y = boardTop + stringSpacing * si
            val strokePx = when (si) {
                5 -> 3.dp.toPx(); 4 -> 2.5.dp.toPx(); 3 -> 2.dp.toPx()
                else -> 1.5.dp.toPx()
            }
            drawLine(
                color       = onSurfaceVar.copy(alpha = 0.55f),
                start       = Offset(boardLeft, y),
                end         = Offset(boardRight, y),
                strokeWidth = strokePx
            )
            val lm = textMeasurer.measure(label, TextStyle(fontSize = 11.sp, color = onSurfaceVar))
            drawText(lm, topLeft = Offset(0f, y - lm.size.height / 2f))
        }

        // ── Nut (first diagram only) ──────────────────────────────────────────
        if (hasOpenString) {
            drawLine(
                color       = onSurface,
                start       = Offset(boardLeft + nutW / 2, boardTop - 4.dp.toPx()),
                end         = Offset(boardLeft + nutW / 2, boardBottom + 4.dp.toPx()),
                strokeWidth = nutW,
                cap         = StrokeCap.Square
            )
        }

        // ── Fret wires ───────────────────────────────────────────────────────
        // Left border for second diagram (no nut)
        if (!hasOpenString) {
            drawLine(
                color       = onSurfaceVar.copy(alpha = 0.4f),
                start       = Offset(boardLeft, boardTop),
                end         = Offset(boardLeft, boardBottom),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        for (i in 1..numFretSlots) {
            val x = boardLeft + nutW + slotW * i
            drawLine(
                color       = onSurfaceVar.copy(alpha = 0.4f),
                start       = Offset(x, boardTop),
                end         = Offset(x, boardBottom),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        // ── Inlay markers (inside fretboard, between strings 3 & 4) ──────────
        val markerY = boardTop + stringSpacing * 2.5f
        for (fret in firstFret..lastFret) {
            if (fret == 0) continue
            val mod = fret % 12
            val slot = fretToSlot(fret)
            when {
                mod in INLAY_SINGLE -> drawCircle(
                    color  = onSurfaceVar.copy(alpha = 0.25f),
                    radius = 5.dp.toPx(),
                    center = Offset(slotX(slot), markerY)
                )
                mod == 0 -> {
                    // Double dot at octave frets (12, 24)
                    drawCircle(onSurfaceVar.copy(alpha = 0.25f), 4.dp.toPx(),
                        Offset(slotX(slot), boardTop + stringSpacing * 1.5f))
                    drawCircle(onSurfaceVar.copy(alpha = 0.25f), 4.dp.toPx(),
                        Offset(slotX(slot), boardTop + stringSpacing * 3.5f))
                }
            }
        }

        // ── Fret numbers ─────────────────────────────────────────────────────
        val labelFrets = buildSet {
            add(firstFret); add(lastFret)
            for (fret in firstFret..lastFret) {
                if (fret == 0) continue
                val mod = fret % 12
                if (mod in INLAY_SINGLE || mod == 0) add(fret)
            }
        }
        for (fret in labelFrets) {
            val slot = fretToSlot(fret)
            val lm = textMeasurer.measure(
                fret.toString(),
                TextStyle(fontSize = 10.sp, color = onSurfaceVar.copy(alpha = 0.6f))
            )
            drawText(lm, topLeft = Offset(slotX(slot) - lm.size.width / 2f, boardBottom + 2.dp.toPx()))
        }

        // ── Note dots ────────────────────────────────────────────────────────
        FB_OPEN_NOTES.forEachIndexed { si, openNote ->
            val y = boardTop + stringSpacing * si
            for (fret in firstFret..lastFret) {
                if ((openNote + fret) % 12 == noteIndex) {
                    val slot = fretToSlot(fret)
                    val x    = slotX(slot)
                    drawCircle(color = primaryColor, radius = dotRadius, center = Offset(x, y))
                    val nm = textMeasurer.measure(
                        FB_NOTE_NAMES[noteIndex],
                        TextStyle(fontSize = 7.sp, color = onPrimary, fontWeight = FontWeight.Bold)
                    )
                    drawText(nm, topLeft = Offset(x - nm.size.width / 2f, y - nm.size.height / 2f))
                }
            }
        }
    }
}
