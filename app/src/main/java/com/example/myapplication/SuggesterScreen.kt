package com.example.myapplication

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Data model ────────────────────────────────────────────────────────────────

private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
private val GENRES = listOf("Rock", "Blues", "Jazz", "Funk", "Metal")

private enum class ChordQuality(val suffix: String) {
    MAJOR(""), MINOR("m"), DOM7("7"), MAJ7("maj7"), MIN7("m7"), DIM("°"), HALF_DIM("ø7")
}

private data class ChordDegree(val semitones: Int, val quality: ChordQuality, val roman: String)
private data class Progression(val name: String, val degrees: List<ChordDegree>)
private data class GenreData(
    val progressions: List<Progression>,
    val scales: List<String>,
    val arpeggios: List<String>
)

private fun chordName(rootIndex: Int, degree: ChordDegree) =
    NOTE_NAMES[(rootIndex + degree.semitones) % 12] + degree.quality.suffix

private fun relativeMinor(rootIndex: Int) = NOTE_NAMES[(rootIndex + 9) % 12] + " minor"
private fun relativeMajor(rootIndex: Int) = NOTE_NAMES[(rootIndex + 3) % 12] + " major"

// ── Genre data ────────────────────────────────────────────────────────────────

private val GENRE_DATA = mapOf(
    "Rock" to GenreData(
        progressions = listOf(
            Progression("Classic Rock", listOf(
                ChordDegree(0, ChordQuality.MAJOR, "I"),
                ChordDegree(5, ChordQuality.MAJOR, "IV"),
                ChordDegree(7, ChordQuality.MAJOR, "V")
            )),
            Progression("Anthem  (I – V – vi – IV)", listOf(
                ChordDegree(0, ChordQuality.MAJOR, "I"),
                ChordDegree(7, ChordQuality.MAJOR, "V"),
                ChordDegree(9, ChordQuality.MINOR, "vi"),
                ChordDegree(5, ChordQuality.MAJOR, "IV")
            )),
            Progression("Minor Rock  (i – bVII – bVI – bVII)", listOf(
                ChordDegree(0, ChordQuality.MINOR, "i"),
                ChordDegree(10, ChordQuality.MAJOR, "bVII"),
                ChordDegree(8, ChordQuality.MAJOR, "bVI"),
                ChordDegree(10, ChordQuality.MAJOR, "bVII")
            )),
            Progression("Grunge  (I – bVII – IV)", listOf(
                ChordDegree(0, ChordQuality.MAJOR, "I"),
                ChordDegree(10, ChordQuality.MAJOR, "bVII"),
                ChordDegree(5, ChordQuality.MAJOR, "IV")
            ))
        ),
        scales = listOf("Pentatonic Minor", "Blues Scale", "Natural Minor (Aeolian)", "Major (Ionian)"),
        arpeggios = listOf("Major Triad  (1 – 3 – 5)", "Minor Triad  (1 – b3 – 5)", "Dominant 7th  (1 – 3 – 5 – b7)")
    ),
    "Blues" to GenreData(
        progressions = listOf(
            Progression("12-Bar Blues  (I7 – IV7 – V7)", listOf(
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7"),
                ChordDegree(7, ChordQuality.DOM7, "V7")
            )),
            Progression("Minor Blues  (i7 – iv7 – V7)", listOf(
                ChordDegree(0, ChordQuality.MIN7, "i7"),
                ChordDegree(5, ChordQuality.MIN7, "iv7"),
                ChordDegree(7, ChordQuality.DOM7, "V7")
            )),
            Progression("Slow Blues  (I7 – IV7 – I7 – V7 – IV7)", listOf(
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7"),
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(7, ChordQuality.DOM7, "V7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7")
            )),
            Progression("Texas Shuffle  (I7 – IV7 – I7 – V7)", listOf(
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7"),
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(7, ChordQuality.DOM7, "V7")
            ))
        ),
        scales = listOf("Blues Scale  (1 – b3 – 4 – b5 – 5 – b7)", "Pentatonic Minor", "Mixolydian", "Dorian"),
        arpeggios = listOf("Dominant 7th  (1 – 3 – 5 – b7)", "Minor 7th  (1 – b3 – 5 – b7)", "Major 6th  (1 – 3 – 5 – 6)")
    ),
    "Jazz" to GenreData(
        progressions = listOf(
            Progression("ii – V – I", listOf(
                ChordDegree(2, ChordQuality.MIN7, "ii7"),
                ChordDegree(7, ChordQuality.DOM7, "V7"),
                ChordDegree(0, ChordQuality.MAJ7, "Imaj7")
            )),
            Progression("Turnaround  (I – vi – ii – V)", listOf(
                ChordDegree(0, ChordQuality.MAJ7, "Imaj7"),
                ChordDegree(9, ChordQuality.MIN7, "vi7"),
                ChordDegree(2, ChordQuality.MIN7, "ii7"),
                ChordDegree(7, ChordQuality.DOM7, "V7")
            )),
            Progression("Rhythm Changes  (I – IV – iii – VI)", listOf(
                ChordDegree(0, ChordQuality.MAJ7, "Imaj7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7"),
                ChordDegree(4, ChordQuality.MIN7, "iii7"),
                ChordDegree(9, ChordQuality.DOM7, "VI7")
            )),
            Progression("Minor ii – V – i", listOf(
                ChordDegree(2, ChordQuality.HALF_DIM, "iiø7"),
                ChordDegree(7, ChordQuality.DOM7, "V7"),
                ChordDegree(0, ChordQuality.MIN7, "im7")
            ))
        ),
        scales = listOf("Dorian", "Mixolydian", "Major (Ionian)", "Lydian", "Bebop Major"),
        arpeggios = listOf(
            "Major 7th  (1 – 3 – 5 – 7)",
            "Minor 7th  (1 – b3 – 5 – b7)",
            "Dominant 7th  (1 – 3 – 5 – b7)",
            "Half-diminished  (1 – b3 – b5 – b7)"
        )
    ),
    "Funk" to GenreData(
        progressions = listOf(
            Progression("Two-Chord Groove  (I7 – IV7)", listOf(
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7")
            )),
            Progression("Minor Funk  (i7 – IV7)", listOf(
                ChordDegree(0, ChordQuality.MIN7, "i7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7")
            )),
            Progression("James Brown  (I7 – bVII7)", listOf(
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(10, ChordQuality.DOM7, "bVII7")
            )),
            Progression("Dorian Vamp  (i7 – iv7)", listOf(
                ChordDegree(0, ChordQuality.MIN7, "i7"),
                ChordDegree(5, ChordQuality.MIN7, "iv7")
            ))
        ),
        scales = listOf("Pentatonic Minor", "Dorian", "Mixolydian"),
        arpeggios = listOf("Dominant 7th  (1 – 3 – 5 – b7)", "Minor 7th  (1 – b3 – 5 – b7)", "9th Chord  (1 – 3 – 5 – b7 – 9)")
    ),
    "Metal" to GenreData(
        progressions = listOf(
            Progression("Power Progression  (i – bVI – bVII)", listOf(
                ChordDegree(0, ChordQuality.MINOR, "i"),
                ChordDegree(8, ChordQuality.MAJOR, "bVI"),
                ChordDegree(10, ChordQuality.MAJOR, "bVII")
            )),
            Progression("Tritone / Dark  (i – bII)", listOf(
                ChordDegree(0, ChordQuality.MINOR, "i"),
                ChordDegree(1, ChordQuality.MAJOR, "bII")
            )),
            Progression("Epic Minor  (i – iv – bVI – bVII)", listOf(
                ChordDegree(0, ChordQuality.MINOR, "i"),
                ChordDegree(5, ChordQuality.MINOR, "iv"),
                ChordDegree(8, ChordQuality.MAJOR, "bVI"),
                ChordDegree(10, ChordQuality.MAJOR, "bVII")
            )),
            Progression("Thrash  (i – bVII – bVI – bVII)", listOf(
                ChordDegree(0, ChordQuality.MINOR, "i"),
                ChordDegree(10, ChordQuality.MAJOR, "bVII"),
                ChordDegree(8, ChordQuality.MAJOR, "bVI"),
                ChordDegree(10, ChordQuality.MAJOR, "bVII")
            ))
        ),
        scales = listOf("Natural Minor (Aeolian)", "Phrygian", "Harmonic Minor", "Pentatonic Minor", "Diminished"),
        arpeggios = listOf(
            "Minor Triad  (1 – b3 – 5)",
            "Diminished  (1 – b3 – b5)",
            "Power Chord  (1 – 5)",
            "Minor 7th  (1 – b3 – 5 – b7)"
        )
    )
)

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
fun SuggesterScreen(modifier: Modifier = Modifier) {
    var selectedGenre by remember { mutableStateOf("Rock") }
    var selectedKey by remember { mutableStateOf("C") }
    var isMajor by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) }

    val rootIndex = NOTE_NAMES.indexOf(selectedKey)
    val data = GENRE_DATA[selectedGenre]!!

    // When Major: relative minor is 9 semitones up (C major → A minor)
    // When Minor: relative major is 3 semitones up (A minor → C major)
    val relativeKeyLabel = if (isMajor)
        "Relative minor: ${NOTE_NAMES[(rootIndex + 9) % 12]} minor"
    else
        "Relative major: ${NOTE_NAMES[(rootIndex + 3) % 12]} major"

    Column(modifier = modifier.fillMaxSize()) {

        // Genre selector
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Text("Genre", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                GENRES.forEach { genre ->
                    FilterChip(
                        selected = selectedGenre == genre,
                        onClick = { selectedGenre = genre },
                        label = { Text(genre) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
        }

        // Key + Major/Minor selector
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(
                    selected = isMajor,
                    onClick = { isMajor = true },
                    label = { Text("Major") }
                )
                FilterChip(
                    selected = !isMajor,
                    onClick = { isMajor = false },
                    label = { Text("Minor") }
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                NOTE_NAMES.forEach { note ->
                    FilterChip(
                        selected = selectedKey == note,
                        onClick = { selectedKey = note },
                        label = { Text(note) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                relativeKeyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Tabs
        TabRow(selectedTabIndex = selectedTab) {
            listOf("Progression", "Scales", "Arpeggios").forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> ProgressionsTab(data.progressions, rootIndex)
            1 -> ItemListTab(data.scales, "scale")
            2 -> ItemListTab(data.arpeggios, "arpeggio")
        }
    }
}

@Composable
private fun ProgressionsTab(progressions: List<Progression>, rootIndex: Int) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        progressions.forEach { progression ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        progression.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        progression.degrees.joinToString(" – ") { it.roman },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        progression.degrees.joinToString(" – ") { chordName(rootIndex, it) },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Chord diagrams:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        progression.degrees
                            .map { chordName(rootIndex, it) }
                            .distinct()
                            .forEach { chord ->
                                TextButton(onClick = {
                                    val query = Uri.encode("$chord chord diagram guitar")
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
                                    )
                                }) {
                                    Text(chord, fontSize = 13.sp)
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemListTab(items: List<String>, type: String) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 4.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val query = Uri.encode("$item $type guitar")
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
                        )
                    }) {
                        Text("Diagram", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
