package com.example.running

import android.os.Bundle
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.min
import android.util.Log


// Importaciones para Wearable Data Layer
import com.google.android.gms.wearable.*
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.ExecutionException

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private lateinit var dataClient: DataClient

    private var mediaPlayer: MediaPlayer? = null

    // Variables compartidas
    internal var _sharedDistance = mutableStateOf(0f)
    internal var _sharedSteps = mutableStateOf(0)
    internal var _sharedCalories = mutableStateOf(0)
    internal var _sharedTime = mutableStateOf(0)
    internal var _sharedGoalAchieved = mutableStateOf(false)
    internal var _sharedIsRunning = mutableStateOf(false)
    internal var _sharedShouldReset = mutableStateOf(false)

    // AGREGAR ESTAS NUEVAS VARIABLES
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted.
                Log.d("Permissions", "Permiso de ubicación precisa concedido")
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted.
                Log.d("Permissions", "Permiso de ubicación aproximada concedido")
            } else -> {
            // No location access granted.
            Log.d("Permissions", "Permisos de ubicación denegados")
        }
        }
    }

    private fun playGoalAchievedSound() {
        try {
            // Liberar MediaPlayer anterior si existe
            mediaPlayer?.release()

            // Usar sonido de notificación del sistema
            val notificationUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer.create(this, notificationUri)

            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
                mediaPlayer = null
            }

            mediaPlayer?.start()
            Log.d("Sound", "Sonido de meta alcanzada reproducido")
        } catch (e: Exception) {
            Log.e("Sound", "Error reproduciendo sonido", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar el cliente de datos
        dataClient = Wearable.getDataClient(this)
        checkWearableConnection()
        @OptIn(ExperimentalMaterial3Api::class)

        // AGREGAR ESTA LÍNEA
        checkLocationPermissions()

        setContent {
            RunningAppSimple(
                onGoalAchieved = { sendGoalAchievedToWear() },
                activity = this@MainActivity
            )
        }
    }

    private fun checkLocationPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Ya tienes los permisos
                Log.d("Permissions", "Permisos de ubicación ya concedidos")
            }
            else -> {
                // Solicitar permisos
                locationPermissionRequest.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }


    override fun onResume() {
        super.onResume()
        dataClient.addListener(this)
    }

    override fun onPause() {
        super.onPause()
        dataClient.removeListener(this)
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        for (event in dataEventBuffer) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                when (dataItem.uri.path) {
                    "/goal_achieved" -> {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val source = dataMap.getString("source")
                        Log.d("Mobile", "Recibida notificación de meta completada desde: $source")

                        // Solo procesar si viene de WearOS
                        if (source == "wear") {
                            runOnUiThread {
                                _sharedGoalAchieved.value = true
                                // Auto-reset después de 3 segundos
                                lifecycleScope.launch {
                                    delay(3000)
                                    _sharedGoalAchieved.value = false
                                }
                            }
                        }
                    }
                    "/running_stats" -> {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val source = dataMap.getString("source", "")
                        Log.d("Mobile", "Recibidas estadísticas desde: $source")

                        // Solo procesar si viene de WearOS
                        if (source == "wear") {
                            runOnUiThread {
                                _sharedDistance.value = dataMap.getFloat("distance", 0f)
                                _sharedSteps.value = dataMap.getInt("steps", 0)
                                _sharedCalories.value = dataMap.getInt("calories", 0)
                                _sharedTime.value = dataMap.getInt("time", 0)
                                Log.d("Mobile", "Estadísticas actualizadas desde WearOS - Distancia: ${dataMap.getFloat("distance", 0f)}")
                            }
                        }
                    }
                    "/running_state" -> {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val source = dataMap.getString("source", "")
                        Log.d("Mobile", "Recibido estado de carrera desde: $source")

                        if (source == "wear") {
                            val isRunning = dataMap.getBoolean("isRunning", false)
                            runOnUiThread {
                                _sharedIsRunning.value = isRunning
                            }
                        }
                    }
                    "/reset_stats" -> {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val source = dataMap.getString("source", "")
                        Log.d("Mobile", "Recibido reset desde: $source")

                        if (source == "wear") {
                            runOnUiThread {
                                _sharedShouldReset.value = true
                                // Auto-reset del flag después de un momento
                                lifecycleScope.launch {
                                    delay(100)
                                    _sharedShouldReset.value = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkWearableConnection() {
        lifecycleScope.launch(Dispatchers.IO) { // AGREGAR Dispatchers.IO
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(this@MainActivity).connectedNodes)
                if (nodes.isNotEmpty()) {
                    Log.d("Connection", "Dispositivos conectados: ${nodes.size}")
                    for (node in nodes) {
                        Log.d("Connection", "Nodo: ${node.displayName} - ${node.id}")
                    }
                } else {
                    Log.w("Connection", "No hay dispositivos Wear conectados")
                }
            } catch (e: Exception) {
                Log.e("Connection", "Error verificando conexión", e)
            }
        }
    }

    internal fun sendStatsToWear(distance: Float, steps: Int, calories: Int, time: Int) {
        lifecycleScope.launch(Dispatchers.IO) { // AGREGAR Dispatchers.IO
            try {
                val putDataRequest = PutDataMapRequest.create("/running_stats").run {
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                    dataMap.putString("source", "mobile")
                    dataMap.putFloat("distance", distance)
                    dataMap.putInt("steps", steps)
                    dataMap.putInt("calories", calories)
                    dataMap.putInt("time", time)
                    asPutDataRequest()
                }

                val result = Tasks.await(dataClient.putDataItem(putDataRequest))
                Log.d("Mobile", "Estadísticas enviadas a WearOS - Distancia: $distance, Pasos: $steps, Calorías: $calories, Tiempo: $time")
            } catch (e: Exception) {
                Log.e("Mobile", "Error enviando estadísticas a WearOS", e)
            }
        }
    }

    internal fun sendRunningStateToWear(isRunning: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val putDataRequest = PutDataMapRequest.create("/running_state").run {
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                    dataMap.putString("source", "mobile")
                    dataMap.putBoolean("isRunning", isRunning)
                    asPutDataRequest()
                }

                val result = Tasks.await(dataClient.putDataItem(putDataRequest))
                Log.d("Mobile", "Estado de carrera enviado a WearOS: $isRunning")
            } catch (e: Exception) {
                Log.e("Mobile", "Error enviando estado de carrera a WearOS", e)
            }
        }
    }

    internal fun sendResetToWear() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val putDataRequest = PutDataMapRequest.create("/reset_stats").run {
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                    dataMap.putString("source", "mobile")
                    dataMap.putString("action", "reset")
                    asPutDataRequest()
                }

                val result = Tasks.await(dataClient.putDataItem(putDataRequest))
                Log.d("Mobile", "Reset enviado a WearOS")
            } catch (e: Exception) {
                Log.e("Mobile", "Error enviando reset a WearOS", e)
            }
        }
    }

    private fun sendGoalAchievedToWear() {
        // AGREGAR ESTA LÍNEA AL INICIO
        playGoalAchievedSound()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val putDataRequest = PutDataMapRequest.create("/goal_achieved").run {
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                    dataMap.putString("source", "mobile")
                    dataMap.putString("message", "Meta completada desde móvil")
                    asPutDataRequest()
                }

                val result = Tasks.await(dataClient.putDataItem(putDataRequest))
                Log.d("Mobile", "Datos enviados a WearOS: ${result.uri}")
            } catch (e: Exception) {
                Log.e("Mobile", "Error enviando datos a WearOS", e)
            }
        }
    }

    }

val darkBlue = Color(0xFF1A237E)
val deepPurple = Color(0xFF4A148C)

@Composable
fun RunningAppSimple(
    onGoalAchieved: () -> Unit = {},
    activity: MainActivity? = null
) {
    var currentScreen by remember { mutableStateOf("main") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF2196F3),
            primaryContainer = Color(0xFF1976D2),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF1E1E1E)
                ) {
                    NavigationBarItem(
                        icon = {
                            Text(
                                text = "🏃‍♂️",
                                fontSize = 24.sp,
                                color = if (currentScreen == "main") Color(0xFF2196F3) else Color.Gray
                            )
                        },
                        label = {
                            Text(
                                "Running",
                                color = if (currentScreen == "main") Color(0xFF2196F3) else Color.Gray
                            )
                        },
                        selected = currentScreen == "main",
                        onClick = { currentScreen = "main" }
                    )
                    NavigationBarItem(
                        icon = {
                            Text(
                                text = "📊",
                                fontSize = 24.sp,
                                color = if (currentScreen == "history") Color(0xFF2196F3) else Color.Gray
                            )
                        },
                        label = {
                            Text(
                                "Historial",
                                color = if (currentScreen == "history") Color(0xFF2196F3) else Color.Gray
                            )
                        },
                        selected = currentScreen == "history",
                        onClick = { currentScreen = "history" }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(darkBlue, deepPurple)
                        )
                    )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Agregar el texto del título aquí
                    Text(
                        text = "🏃‍♂️ Running Tracker",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
                when (currentScreen) {
                    "main" -> MainScreenSimple(
                        onGoalAchieved = onGoalAchieved,
                        sharedDistance = activity?._sharedDistance?.value ?: 0f,
                        sharedSteps = activity?._sharedSteps?.value ?: 0,
                        sharedCalories = activity?._sharedCalories?.value ?: 0,
                        sharedTime = activity?._sharedTime?.value ?: 0,
                        sharedGoalAchieved = activity?._sharedGoalAchieved?.value ?: false,
                        sharedIsRunning = activity?._sharedIsRunning?.value ?: false,
                        sharedShouldReset = activity?._sharedShouldReset?.value ?: false,
                        onSendStats = { distance, steps, calories, time ->
                            activity?.sendStatsToWear(distance, steps, calories, time)
                        },
                        onResetSharedGoal = {
                            activity?._sharedGoalAchieved?.value = false
                        },
                        onSendRunningState = { isRunning ->
                            activity?.sendRunningStateToWear(isRunning)
                        },
                        onSendReset = {
                            activity?.sendResetToWear()
                        },
                        onPlaySound = {
                            // activity?.playGoalAchievedSound()
                        }
                    )
                    "history" -> HistoryScreenSimple()
                }
            }
        }
    }
}

    @Composable
    fun MainScreenSimple(
        onGoalAchieved: () -> Unit = {},
        sharedDistance: Float = 0f,
        sharedSteps: Int = 0,
        sharedCalories: Int = 0,
        sharedTime: Int = 0,
        sharedGoalAchieved: Boolean = false,
        sharedIsRunning: Boolean = false,
        sharedShouldReset: Boolean = false,
        onSendStats: (Float, Int, Int, Int) -> Unit = { _, _, _, _ -> },
        onResetSharedGoal: () -> Unit = {},
        onSendRunningState: (Boolean) -> Unit = {},
        onSendReset: () -> Unit = {},
        onPlaySound: () -> Unit = {}
    ) {
        var isRunning by remember { mutableStateOf(false) }
        var distance by remember { mutableStateOf(0f) }
        var totalTime by remember { mutableStateOf(0) }
        var calories by remember { mutableStateOf(0) }
        var heartRate by remember { mutableStateOf(90) }
        var steps by remember { mutableStateOf(0) }
        var showGoalAchievedAlert by remember { mutableStateOf(false) }
        var goalAchieved by remember { mutableStateOf(false) }

        val dailyGoal = 5.0f

        // Efectos LaunchedEffect (mantener la funcionalidad original)
        LaunchedEffect(isRunning) {
            while (isRunning) {
                delay(1000)
                distance += 0.003f
                totalTime += 1
                calories += 1
                heartRate = (140..160).random()
                steps += 2

                if (distance >= dailyGoal && !goalAchieved) {
                    goalAchieved = true
                    showGoalAchievedAlert = true
                    onGoalAchieved()
                }
            }
        }

        LaunchedEffect(isRunning) {
            if (!isRunning && heartRate > 100) {
                while (heartRate > 90 && !isRunning) {
                    delay(2000)
                    heartRate = maxOf(90, heartRate - 5)
                }
            }
        }

        LaunchedEffect(sharedGoalAchieved) {
            if (sharedGoalAchieved && !goalAchieved) {
                showGoalAchievedAlert = true
                onPlaySound()
            }
        }

        LaunchedEffect(isRunning, distance, steps, calories, totalTime) {
            if (isRunning) {
                while (isRunning) {
                    delay(3000)
                    onSendStats(distance, steps, calories, totalTime)
                }
            } else {
                if (distance > 0 || steps > 0 || calories > 0 || totalTime > 0) {
                    onSendStats(distance, steps, calories, totalTime)
                }
            }
        }

        LaunchedEffect(distance, steps, calories, totalTime) {
            if (!isRunning && (distance > 5.0f || steps > 7000)) {
                onSendStats(distance, steps, calories, totalTime)
                delay(500)
            }
        }

        LaunchedEffect(sharedDistance, sharedSteps, sharedCalories, sharedTime) {
            if (!isRunning && (sharedDistance > 0 || sharedSteps > 0 || sharedCalories > 0 || sharedTime > 0)) {
                var updated = false
                if (sharedDistance > distance) {
                    distance = sharedDistance
                    updated = true
                }
                if (sharedSteps > steps) {
                    steps = sharedSteps
                    updated = true
                }
                if (sharedCalories > calories) {
                    calories = sharedCalories
                    updated = true
                }
                if (sharedTime > totalTime) {
                    totalTime = sharedTime
                    updated = true
                }
            }
        }

        LaunchedEffect(sharedIsRunning) {
            if (sharedIsRunning != isRunning) {
                isRunning = sharedIsRunning
            }
        }

        LaunchedEffect(sharedShouldReset) {
            if (sharedShouldReset) {
                distance = 0f
                totalTime = 0
                calories = 0
                heartRate = 90
                steps = 0
                isRunning = false
                goalAchieved = false
                showGoalAchievedAlert = false
            }
        }

        val progress = min(distance / dailyGoal, 1.0f)
        val progressPercentage = (progress * 100).toInt()

        val minutes = totalTime / 60
        val seconds = totalTime % 60
        val timeFormatted = String.format("%02d:%02d", minutes, seconds)

        val pace = if (distance > 0 && totalTime > 0) {
            val paceInSeconds = (totalTime / distance) / 60.0
            val paceMinutes = paceInSeconds.toInt()
            val paceSecondsValue = ((paceInSeconds - paceMinutes) * 60).toInt()
            "${paceMinutes}'${String.format("%02d", paceSecondsValue)}\""
        } else {
            "0'00\""
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(Color(0xFF121212))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tarjeta de progreso simple
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2196F3), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎯 Meta Diaria ${dailyGoal.toInt()}km",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        color = Color(0xFF4CAF50),
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$progressPercentage% completado",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Distancia principal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(20.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "%.2f km".format(distance),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3)
                    )

                    if (isRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("❤️", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$heartRate bpm",
                                fontSize = 18.sp,
                                color = Color(0xFFF44336),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grid de estadísticas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = "⏱️",
                    value = timeFormatted,
                    label = "Tiempo",
                    backgroundColor = Color(0xFF1E1E1E)
                )

                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = "⚡",
                    value = pace,
                    label = "Ritmo/km",
                    backgroundColor = Color(0xFF1E1E1E)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = "🔥",
                    value = "$calories",
                    label = "Calorías",
                    backgroundColor = Color(0xFF1E1E1E)
                )

                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = "👣",
                    value = "$steps",
                    label = "Pasos",
                    backgroundColor = Color(0xFF1E1E1E)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Controles
            Text(
                text = "Controles",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Start/Stop button
                Button(
                    onClick = {
                        isRunning = !isRunning
                        onSendRunningState(isRunning)
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFFF44336) else Color(0xFF4CAF50)
                    )
                ) {
                    Text(
                        text = if (isRunning) "⏸️" else "▶️",
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "Pausar" else "Iniciar",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Simulación 5km
                    Button(
                        onClick = {
                            distance = 5.1f
                            totalTime = 1800
                            calories = 450
                            heartRate = 155
                            steps = 7500
                            if (!goalAchieved) {
                                goalAchieved = true
                                showGoalAchievedAlert = true
                                onGoalAchieved()
                                onPlaySound()
                            }
                            onSendStats(5.1f, 7500, 450, 1800)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Text("🚀 5km", color = Color.White, fontWeight = FontWeight.Medium)
                    }

                    // Reset
                    Button(
                        onClick = {
                            distance = 0f
                            totalTime = 0
                            calories = 0
                            heartRate = 90
                            steps = 0
                            isRunning = false
                            goalAchieved = false
                            showGoalAchievedAlert = false
                            onSendReset()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Text("🔄 Reset", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Alerta de meta alcanzada
        if (showGoalAchievedAlert) {
            AlertDialog(
                onDismissRequest = { showGoalAchievedAlert = false },
                title = {
                    Text(
                        text = "🏆 ¡Meta Alcanzada!",
                        color = Color(0xFF2196F3),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "¡Felicidades! Has completado ${dailyGoal.toInt()}km hoy.",
                        color = Color.White
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showGoalAchievedAlert = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("¡Genial!", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF1E1E1E)
            )
        }
    }

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: String,
    value: String,
    label: String,
    backgroundColor: Color
) {
    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun HistoryScreenSimple() {
    val dailyKilometers = remember {
        mapOf(
            "Lunes" to 3.2f,
            "Martes" to 5.1f,
            "Miércoles" to 2.8f,
            "Jueves" to 4.5f,
            "Viernes" to 6.2f,
            "Sábado" to 1.8f,
            "Domingo" to 7.3f
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        // Tarjeta de resumen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2196F3), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "📊 Resumen Semanal",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryItem(
                        value = "${String.format("%.1f", dailyKilometers.values.sum())} km",
                        label = "Total"
                    )
                    SummaryItem(
                        value = "${String.format("%.1f", dailyKilometers.values.average())} km",
                        label = "Promedio"
                    )
                    SummaryItem(
                        value = "${dailyKilometers.values.count { it >= 5.0f }}",
                        label = "Metas"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lista de días
        dailyKilometers.forEach { (day, km) ->
            HistoryItem(
                day = day,
                kilometers = km,
                goalAchieved = km >= 5.0f,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
}

@Composable
fun SummaryItem(
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun HistoryItem(
    day: String,
    kilometers: Float,
    goalAchieved: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = day,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${String.format("%.1f", kilometers)} km",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (goalAchieved) Color(0xFF4CAF50) else Color.White
                )

                if (goalAchieved) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⭐",
                        fontSize = 20.sp
                    )
                }
            }
        }
    }
}