package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

enum class AppMode {
    TUNER,
    KEY_FINDER,
    METRONOME
}

class MainActivity : ComponentActivity() {

    private var dispatcher: AudioDispatcher? = null
    private var tunerNote by mutableStateOf("--")
    private var isRecording by mutableStateOf(false)

    // State for Key Finder
    private val detectedKeyNotes = mutableStateListOf<String>()
    private var foundKey by mutableStateOf("")
    private var lastKeyNoteDetectionTime by mutableStateOf(0L)

    // State for Metronome
    private var isMetronomeRunning by mutableStateOf(false)
    private var metronomeBpm by mutableStateOf(120)
    private var isBeat by mutableStateOf(false)
    private val metronomeScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var metronomeJob: Job? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // For simplicity, we can just let the user re-tap the button.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(
                    tunerNote = tunerNote,
                    isRecording = isRecording,
                    detectedKeyNotes = detectedKeyNotes,
                    foundKey = foundKey,
                    isMetronomeRunning = isMetronomeRunning,
                    metronomeBpm = metronomeBpm,
                    isBeat = isBeat,
                    onStartRecording = ::startRecording,
                    onStopRecording = ::stopRecording,
                    onResetKeyFinder = ::resetKeyFinder,
                    onStartMetronome = ::startMetronome,
                    onStopMetronome = ::stopMetronome,
                    onBpmChange = { metronomeBpm = it }
                )
            }
        }
    }

    private fun startRecording(mode: AppMode) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        isRecording = true
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 1024, 0)
        val pitchDetectionHandler = PitchDetectionHandler { res, _ ->
            val pitchInHz = res.pitch
            if (pitchInHz != -1f) {
                val a4 = 440.0
                val midiNote = (12 * (Math.log(pitchInHz.toDouble() / a4) / Math.log(2.0)) + 69).roundToInt()
                val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                val detectedNote = noteNames[midiNote % 12]

                runOnUiThread {
                    when (mode) {
                        AppMode.TUNER -> {
                            val displayNoteNames = arrayOf("C", "C# / Db", "D", "D# / Eb", "E", "F", "F# / Gb", "G", "G# / Ab", "A", "A# / Bb", "B")
                            tunerNote = displayNoteNames[midiNote % 12]
                        }
                        AppMode.KEY_FINDER -> {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastKeyNoteDetectionTime > 2000) { // 2 second cooldown
                                if (detectedNote.isNotEmpty() && !detectedKeyNotes.contains(detectedNote)) {
                                    detectedKeyNotes.add(detectedNote)
                                    lastKeyNoteDetectionTime = currentTime
                                }
                            }

                            if (detectedKeyNotes.size >= 3) {
                                findKeyFromNotes()
                                stopRecording()
                            }
                        }
                        AppMode.METRONOME -> {}
                    }
                }
            }
        }
        val pitchProcessor = PitchProcessor(PitchEstimationAlgorithm.YIN, 22050f, 1024, pitchDetectionHandler)
        dispatcher?.addAudioProcessor(pitchProcessor)
        dispatcher?.let { Thread(it, "Audio Dispatcher").start() }
    }

    private fun stopRecording() {
        isRecording = false
        dispatcher?.stop()
    }

    private fun resetKeyFinder() {
        detectedKeyNotes.clear()
        foundKey = ""
        lastKeyNoteDetectionTime = 0L
        if (isRecording) stopRecording()
    }

    private fun findKeyFromNotes() {
        val possibleKeys = musicTheory.keys.filter {
            val scaleNotes = musicTheory[it]
            detectedKeyNotes.all { detectedNote -> scaleNotes?.contains(detectedNote) == true }
        }
        foundKey = if (possibleKeys.isNotEmpty()) possibleKeys.joinToString(", ") else "No matching key found"
    }

    private fun startMetronome() {
        isMetronomeRunning = true
        metronomeJob = metronomeScope.launch {
            val sampleRate = 44100
            val clickBuffer = generateClick(sampleRate)
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(clickBuffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            audioTrack.write(clickBuffer, 0, clickBuffer.size)

            try {
                while (isActive) {
                    val intervalMs = 60000L / metronomeBpm
                    val tickStart = System.currentTimeMillis()

                    audioTrack.stop()
                    audioTrack.reloadStaticData()
                    audioTrack.play()
                    isBeat = true
                    delay(80)
                    isBeat = false

                    val elapsed = System.currentTimeMillis() - tickStart
                    val remaining = intervalMs - elapsed
                    if (remaining > 0) delay(remaining)
                }
            } finally {
                audioTrack.stop()
                audioTrack.release()
            }
        }
    }

    private fun stopMetronome() {
        metronomeJob?.cancel()
        isMetronomeRunning = false
        isBeat = false
    }

    private fun generateClick(sampleRate: Int): ShortArray {
        val numSamples = sampleRate * 30 / 1000  // 30ms click
        val buffer = ShortArray(numSamples)
        val freq = 1000.0
        for (i in 0 until numSamples) {
            val envelope = 1.0 - i.toDouble() / numSamples
            buffer[i] = (envelope * sin(2 * PI * freq * i / sampleRate) * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    override fun onStop() {
        super.onStop()
        stopRecording()
        stopMetronome()
    }

    override fun onDestroy() {
        super.onDestroy()
        metronomeScope.cancel()
    }

    companion object {
        val musicTheory = mapOf(
            "C Major" to listOf("C", "D", "E", "F", "G", "A", "B"),
            "G Major" to listOf("G", "A", "B", "C", "D", "E", "F#"),
            "D Major" to listOf("D", "E", "F#", "G", "A", "B", "C#"),
            "A Major" to listOf("A", "B", "C#", "D", "E", "F#", "G#"),
            "E Major" to listOf("E", "F#", "G#", "A", "B", "C#", "D#"),
            "B Major" to listOf("B", "C#", "D#", "E", "F#", "G#", "A#"),
            "F# Major" to listOf("F#", "G#", "A#", "B", "C#", "D#", "E#"),
            "C# Major" to listOf("C#", "D#", "E#", "F#", "G#", "A#", "B#"),

            "F Major" to listOf("F", "G", "A", "Bb", "C", "D", "E"),
            "Bb Major" to listOf("Bb", "C", "D", "Eb", "F", "G", "A"),
            "Eb Major" to listOf("Eb", "F", "G", "Ab", "Bb", "C", "D"),
            "Ab Major" to listOf("Ab", "Bb", "C", "Db", "Eb", "F", "G"),
            "Db Major" to listOf("Db", "Eb", "F", "Gb", "Ab", "Bb", "C"),
            "Gb Major" to listOf("Gb", "Ab", "Bb", "Cb", "Db", "Eb", "F"),

            "A Minor" to listOf("A", "B", "C", "D", "E", "F", "G"),
            "E Minor" to listOf("E", "F#", "G", "A", "B", "C", "D"),
            "B Minor" to listOf("B", "C#", "D", "E", "F#", "G", "A"),
            "F# Minor" to listOf("F#", "G#", "A", "B", "C#", "D", "E"),
            "C# Minor" to listOf("C#", "D#", "E", "F#", "G#", "A", "B"),
            "G# Minor" to listOf("G#", "A#", "B", "C#", "D#", "E", "F#"),
            "D# Minor" to listOf("D#", "E#", "F#", "G#", "A#", "B", "C#"),

            "D Minor" to listOf("D", "E", "F", "G", "A", "Bb", "C"),
            "G Minor" to listOf("G", "A", "Bb", "C", "D", "Eb", "F"),
            "C Minor" to listOf("C", "D", "Eb", "F", "G", "Ab", "Bb"),
            "F Minor" to listOf("F", "G", "Ab", "Bb", "C", "Db", "Eb"),
            "Bb Minor" to listOf("Bb", "C", "Db", "Eb", "F", "Gb", "Ab"),
            "Eb Minor" to listOf("Eb", "F", "Gb", "Ab", "Bb", "Cb", "Db"),
        )
    }
}

@Composable
fun MainScreen(
    tunerNote: String,
    isRecording: Boolean,
    detectedKeyNotes: List<String>,
    foundKey: String,
    isMetronomeRunning: Boolean,
    metronomeBpm: Int,
    isBeat: Boolean,
    onStartRecording: (AppMode) -> Unit,
    onStopRecording: () -> Unit,
    onResetKeyFinder: () -> Unit,
    onStartMetronome: () -> Unit,
    onStopMetronome: () -> Unit,
    onBpmChange: (Int) -> Unit
) {
    var appMode by remember { mutableStateOf(AppMode.TUNER) }

    fun switchMode(mode: AppMode) {
        onResetKeyFinder()
        onStopMetronome()
        appMode = mode
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            ModeSelector(selectedMode = appMode, onModeSelected = ::switchMode)
            when (appMode) {
                AppMode.TUNER -> TunerScreen(
                    note = tunerNote,
                    isRecording = isRecording,
                    onStartRecording = { onStartRecording(AppMode.TUNER) },
                    onStopRecording = onStopRecording
                )
                AppMode.KEY_FINDER -> KeyFinderScreen(
                    notes = detectedKeyNotes,
                    isRecording = isRecording,
                    foundKey = foundKey,
                    onStart = { onStartRecording(AppMode.KEY_FINDER) },
                    onReset = onResetKeyFinder
                )
                AppMode.METRONOME -> MetronomeScreen(
                    bpm = metronomeBpm,
                    isRunning = isMetronomeRunning,
                    isBeat = isBeat,
                    onBpmChange = onBpmChange,
                    onStart = onStartMetronome,
                    onStop = onStopMetronome
                )
            }
        }
    }
}

@Composable
fun ModeSelector(selectedMode: AppMode, onModeSelected: (AppMode) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = { onModeSelected(AppMode.TUNER) }, enabled = selectedMode != AppMode.TUNER) {
            Text("Tuner")
        }
        TextButton(onClick = { onModeSelected(AppMode.KEY_FINDER) }, enabled = selectedMode != AppMode.KEY_FINDER) {
            Text("Key Finder")
        }
        TextButton(onClick = { onModeSelected(AppMode.METRONOME) }, enabled = selectedMode != AppMode.METRONOME) {
            Text("Metronome")
        }
    }
}

@Composable
fun MetronomeScreen(
    bpm: Int,
    isRunning: Boolean,
    isBeat: Boolean,
    onBpmChange: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val beatColor = if (isBeat) Color.Green else Color(0xFF444444)

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Metronome", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(beatColor)
        )
        Spacer(Modifier.height(32.dp))

        Text(
            text = "$bpm BPM",
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        Slider(
            value = bpm.toFloat(),
            onValueChange = { onBpmChange(it.roundToInt()) },
            valueRange = 40f..240f,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onBpmChange((bpm - 10).coerceAtLeast(40)) }) { Text("-10") }
            OutlinedButton(onClick = { onBpmChange((bpm - 1).coerceAtLeast(40)) }) { Text("-1") }
            OutlinedButton(onClick = { onBpmChange((bpm + 1).coerceAtMost(240)) }) { Text("+1") }
            OutlinedButton(onClick = { onBpmChange((bpm + 10).coerceAtMost(240)) }) { Text("+10") }
        }
        Spacer(Modifier.height(32.dp))

        Button(onClick = if (isRunning) onStop else onStart) {
            Text(if (isRunning) "Stop" else "Start")
        }
    }
}

@Composable
fun TunerScreen(
    modifier: Modifier = Modifier,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    note: String,
    isRecording: Boolean
) {
    var animatedEllipsis by remember { mutableStateOf("") }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                animatedEllipsis = "."
                delay(300)
                animatedEllipsis = ".."
                delay(300)
                animatedEllipsis = "..."
                delay(300)
            }
        } else {
            animatedEllipsis = ""
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Chordify",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.Green
        )

        Spacer(Modifier.height(128.dp))

        Text(
            text = note,
            fontSize = 96.sp
        )

        Box(modifier = Modifier.height(48.dp)) {
            if (isRecording) {
                Text(
                    text = "Listening$animatedEllipsis",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(Modifier.height(80.dp))

        if (isRecording) {
            Button(onClick = onStopRecording) {
                Text("Stop Recording")
            }
        } else {
            Button(onClick = onStartRecording) {
                Text("Start Recording")
            }
        }
    }
}

@Composable
fun KeyFinderScreen(
    modifier: Modifier = Modifier,
    notes: List<String>,
    isRecording: Boolean,
    foundKey: String,
    onStart: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Key Finder", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))

        val instructionText = when {
            isRecording && notes.size < 3 -> "Play ${3 - notes.size} more note(s)..."
            notes.size >= 3 -> "Done!"
            else -> "Play 3 notes to detect the key."
        }
        Text(instructionText)
        Spacer(Modifier.height(32.dp))

        val buttonText = when {
            isRecording -> "Listening..."
            notes.size >= 3 -> "Reset"
            else -> "Start Detection"
        }
        Button(onClick = if (notes.size >= 3) onReset else onStart, enabled = !isRecording) {
            Text(buttonText)
        }
        Spacer(Modifier.height(48.dp))

        Text("Detected Notes: [${notes.joinToString(", ")}]", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))

        if (foundKey.isNotEmpty()) {
            Text("Result: $foundKey", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TunerScreenPreview() {
    MyApplicationTheme {
        TunerScreen(onStartRecording = {}, onStopRecording = {}, note = "A# / Bb", isRecording = true)
    }
}

@Preview(showBackground = true)
@Composable
fun KeyFinderScreenPreview() {
    MyApplicationTheme {
        KeyFinderScreen(notes = listOf("C", "G", "E"), isRecording = false, foundKey = "C Major", onStart = {}, onReset = {})
    }
}

@Preview(showBackground = true)
@Composable
fun MetronomeScreenPreview() {
    MyApplicationTheme {
        MetronomeScreen(bpm = 120, isRunning = false, isBeat = false, onBpmChange = {}, onStart = {}, onStop = {})
    }
}
