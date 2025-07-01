package com.example.running.presentation

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.media.ToneGenerator
import android.media.AudioManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.running.presentation.theme.RunningTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.util.Log
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.wear.compose.material.TimeText
import kotlin.math.abs
import kotlin.math.min

// Importaciones para Wearable Data Layer
import com.google.android.gms.wearable.*
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.ExecutionException

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private lateinit var dataClient: DataClient

    private var hasLocationPermission by mutableStateOf(false)
    private var isFirstLaunch by mutableStateOf(true)

    // Launcher para solicitar permisos
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (hasLocationPermission) {
            saveFirstLaunchComplete()
        }
    }

    // Variables compartidas - CAMBIAR ESTAS LÍNEAS
    internal var _sharedDistance = mutableStateOf(0f)
    internal var _sharedSteps = mutableStateOf(0)
    internal var _sharedCalories = mutableStateOf(0)
    internal var _sharedTime = mutableStateOf(0)
    internal var _sharedGoalAchieved = mutableStateOf(false)
    internal var _sharedIsRunning = mutableStateOf(false)
    internal var _sharedShouldReset = mutableStateOf(false)

    // Variable para el callback - AGREGAR ESTA LÍNEA
    private var updateSharedState: ((Float, Int, Int, Int, Boolean, Boolean, Boolean) -> Unit)? = null

    private fun isFirstTimeLaunch(): Boolean {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("first_launch", true)
    }

    private fun saveFirstLaunchComplete() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("first_launch", false).apply()
        isFirstLaunch = false
    }

    private fun playGoalAchievedSound() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            // Reproducir 3 pitidos cortos
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            lifecycleScope.launch {
                delay(300)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                delay(300)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                delay(500)
                toneGenerator.release()
            }
        } catch (e: Exception) {
            Log.e("WearOS", "Error reproduciendo sonido", e)
        }
    }

    private fun checkLocationPermissions() {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar el cliente de datos
        dataClient = Wearable.getDataClient(this)
        checkWearableConnection()

        // Verificar si es la primera vez
        isFirstLaunch = isFirstTimeLaunch()

        // Verificar permisos actuales
        checkLocationPermissions()

        // Si es primera vez y no tiene permisos, solicitarlos
        if (isFirstLaunch && !hasLocationPermission) {
            requestLocationPermissions()
        }

        setContent {
            WearApp(
                onGoalAchieved = {
                    sendGoalAchievedToMobile()
                    playGoalAchievedSound() // AGREGAR ESTA LÍNEA
                },
                onUpdateSharedState = { updateFn ->
                    updateSharedState = updateFn
                },
                onSendStats = { distance, steps, calories, time ->
                    sendStatsToMobile(distance, steps, calories, time)
                },
                onSendRunningState = { isRunning ->
                    sendRunningStateToMobile(isRunning)
                },
                onSendReset = {
                    sendResetToMobile()
                },
                activity = this@MainActivity,
                hasLocationPermission = hasLocationPermission, // NUEVA LÍNEA
                onRequestPermission = { requestLocationPermissions() } // NUEVA LÍNEA
            )
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
                        Log.d("WearOS", "Recibida notificación de meta completada desde: $source")

                        if (source == "mobile") {
                            runOnUiThread {
                                _sharedGoalAchieved.value = true
                                updateSharedState?.invoke(
                                    _sharedDistance.value,
                                    _sharedSteps.value,
                                    _sharedCalories.value,
                                    _sharedTime.value,
                                    true,
                                    _sharedIsRunning.value,
                                    _sharedShouldReset.value
                                )
                            }
                        }
                    }
                    "/running_stats" -> {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val source = dataMap.getString("source", "")
                        Log.d("WearOS", "Recibidas estadísticas desde: $source")

                        if (source == "mobile") {
                            runOnUiThread {
                                _sharedDistance.value = dataMap.getFloat("distance", 0f)
                                _sharedSteps.value = dataMap.getInt("steps", 0)
                                _sharedCalories.value = dataMap.getInt("calories", 0)
                                _sharedTime.value = dataMap.getInt("time", 0)

                                updateSharedState?.invoke(
                                    _sharedDistance.value,
                                    _sharedSteps.value,
                                    _sharedCalories.value,
                                    _sharedTime.value,
                                    _sharedGoalAchieved.value,
                                    _sharedIsRunning.value,
                                    _sharedShouldReset.value
                                )
                                Log.d("WearOS", "Estadísticas sincronizadas desde móvil")
                            }
                        }
                    }
                    "/running_state" -> {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val source = dataMap.getString("source", "")
                        Log.d("WearOS", "Recibido estado de carrera desde: $source")

                        if (source == "mobile") {
                            val isRunning = dataMap.getBoolean("isRunning", false)
                            runOnUiThread {
                                _sharedIsRunning.value = isRunning
                                updateSharedState?.invoke(
                                    _sharedDistance.value,
                                    _sharedSteps.value,
                                    _sharedCalories.value,
                                    _sharedTime.value,
                                    _sharedGoalAchieved.value,
                                    isRunning,
                                    _sharedShouldReset.value
                                )
                            }
                        }
                    }
                    "/reset_stats" -> {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val source = dataMap.getString("source", "")
                        Log.d("WearOS", "Recibido reset desde: $source")

                        if (source == "mobile") {
                            runOnUiThread {
                                _sharedShouldReset.value = true
                                updateSharedState?.invoke(
                                    0f, 0, 0, 0, false, false, true
                                )
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

    private fun sendStatsToMobile(distance: Float, steps: Int, calories: Int, time: Int) {
        lifecycleScope.launch(Dispatchers.IO) { // AGREGAR Dispatchers.IO
            try {
                val putDataRequest = PutDataMapRequest.create("/running_stats").run {
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                    dataMap.putString("source", "wear")
                    dataMap.putFloat("distance", distance)
                    dataMap.putInt("steps", steps)
                    dataMap.putInt("calories", calories)
                    dataMap.putInt("time", time)
                    asPutDataRequest()
                }

                val result = Tasks.await(dataClient.putDataItem(putDataRequest))
                Log.d("WearOS", "Estadísticas enviadas a móvil: ${result.uri}")
            } catch (e: Exception) {
                Log.e("WearOS", "Error enviando estadísticas a móvil", e)
            }
        }
    }

    private fun sendRunningStateToMobile(isRunning: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val putDataRequest = PutDataMapRequest.create("/running_state").run {
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                    dataMap.putString("source", "wear")
                    dataMap.putBoolean("isRunning", isRunning)
                    asPutDataRequest()
                }

                val result = Tasks.await(dataClient.putDataItem(putDataRequest))
                Log.d("WearOS", "Estado de carrera enviado a móvil: $isRunning")
            } catch (e: Exception) {
                Log.e("WearOS", "Error enviando estado de carrera a móvil", e)
            }
        }
    }

    private fun sendResetToMobile() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val putDataRequest = PutDataMapRequest.create("/reset_stats").run {
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                    dataMap.putString("source", "wear")
                    dataMap.putString("action", "reset")
                    asPutDataRequest()
                }

                val result = Tasks.await(dataClient.putDataItem(putDataRequest))
                Log.d("WearOS", "Reset enviado a móvil")
            } catch (e: Exception) {
                Log.e("WearOS", "Error enviando reset a móvil", e)
            }
        }
    }

    private fun sendGoalAchievedToMobile() {
        lifecycleScope.launch(Dispatchers.IO) { // AGREGAR Dispatchers.IO
            try {
                val putDataRequest = PutDataMapRequest.create("/goal_achieved").run {
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                    dataMap.putString("source", "wear")
                    dataMap.putString("message", "Meta completada desde WearOS")
                    asPutDataRequest()
                }

                val result = Tasks.await(dataClient.putDataItem(putDataRequest))
                Log.d("WearOS", "Datos enviados a móvil: ${result.uri}")
            } catch (e: ExecutionException) {
                Log.e("WearOS", "Error enviando datos a móvil", e)
            } catch (e: InterruptedException) {
                Log.e("WearOS", "Error enviando datos a móvil", e)
            } catch (e: Exception) {
                Log.e("WearOS", "Error enviando datos a móvil", e)
            }
        }
    }

}


@Composable
fun WearApp(
    onGoalAchieved: () -> Unit = {},
    onUpdateSharedState: ((Float, Int, Int, Int, Boolean, Boolean, Boolean) -> Unit) -> Unit = {},
    onSendStats: (Float, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onSendRunningState: (Boolean) -> Unit = {},
    onSendReset: () -> Unit = {},
    activity: MainActivity? = null,
    hasLocationPermission: Boolean = true, // NUEVA LÍNEA
    onRequestPermission: () -> Unit = {} // NUEVA LÍNEA
) {
    var currentScreen by remember { mutableStateOf("main") }

    // Variables compartidas
    var sharedDistance by remember { mutableStateOf(0f) }
    var sharedSteps by remember { mutableStateOf(0) }
    var sharedCalories by remember { mutableStateOf(0) }
    var sharedTime by remember { mutableStateOf(0) }
    var sharedGoalAchieved by remember { mutableStateOf(false) }
    var sharedIsRunning by remember { mutableStateOf(false) }
    var sharedShouldReset by remember { mutableStateOf(false) }

    // Configurar la función de actualización - CAMBIAR COMPLETAMENTE
    LaunchedEffect(Unit) {
        onUpdateSharedState { distance, steps, calories, time, goalAchieved, isRunning, shouldReset ->
            sharedDistance = distance
            sharedSteps = steps
            sharedCalories = calories
            sharedTime = time
            sharedGoalAchieved = goalAchieved
            sharedIsRunning = isRunning
            sharedShouldReset = shouldReset
        }
    }

    MaterialTheme {
        // Mostrar pantalla de permisos si es necesario
        if (!hasLocationPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "📍 Permiso de Ubicación",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Esta app necesita acceso a tu ubicación para simular distancias recorridas",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Button(
                    onClick = onRequestPermission
                ) {
                    Text("Permitir")
                }
            }
            return@MaterialTheme
        }
        Column(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                "main" -> MainScreen(
                    onNavigateToSecond = { currentScreen = "second" },
                    onGoalAchieved = onGoalAchieved,
                    sharedDistance = activity?._sharedDistance?.value ?: sharedDistance,
                    sharedSteps = activity?._sharedSteps?.value ?: sharedSteps,
                    sharedCalories = activity?._sharedCalories?.value ?: sharedCalories,
                    sharedTime = activity?._sharedTime?.value ?: sharedTime,
                    sharedGoalAchieved = activity?._sharedGoalAchieved?.value ?: sharedGoalAchieved,
                    sharedIsRunning = activity?._sharedIsRunning?.value ?: sharedIsRunning,
                    sharedShouldReset = activity?._sharedShouldReset?.value ?: sharedShouldReset,
                    onSendStats = onSendStats,
                    onSendRunningState = onSendRunningState,
                    onSendReset = onSendReset,
                    onResetSharedGoal = {
                        activity?._sharedGoalAchieved?.value = false
                        activity?._sharedShouldReset?.value = false
                    }
                )
                "second" -> SecondScreenWear(onNavigateBack = { currentScreen = "main" })
            }
        }
    }
}

@Composable
fun MainScreen(
    onNavigateToSecond: () -> Unit,
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
    onSendReset: () -> Unit = {}
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

    // Colores personalizados (como en tu versión anterior)
    val darkBlue = Color(0xFF0F1C3F)
    val deepPurple = Color(0xFF3A1D6E)
    val accentColor = Color(0xFF00E0FF)
    val textColor = Color.White
    val successColor = Color(0xFF00FF88)
    val warningColor = Color(0xFFFF6B35)
    val errorColor = Color.Red
    val grayColor = Color(0xFF888888)

    // AGREGAR este LaunchedEffect DESPUÉS de las variables:
    // Sincronización bidireccional mejorada
    LaunchedEffect(sharedDistance, sharedSteps, sharedCalories, sharedTime) {
        if (!isRunning) {
            // Solo sincronizar si hay datos válidos del móvil
            if (sharedDistance > distance || sharedSteps > steps ||
                sharedCalories > calories || sharedTime > totalTime) {
                distance = sharedDistance
                steps = sharedSteps
                calories = sharedCalories
                totalTime = sharedTime
                Log.d("WearOS", "Sincronizado desde móvil: ${sharedDistance}km")
            }
        }
    }

    // Simulación de datos en tiempo real
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
                onGoalAchieved() // Esto ahora incluye el sonido
            }
        }
    }

    // Resetear ritmo cardíaco
    LaunchedEffect(isRunning) {
        if (!isRunning && heartRate > 100) {
            while (heartRate > 90 && !isRunning) {
                delay(2000)
                heartRate = maxOf(90, heartRate - 5)
            }
        }
    }

    // Manejar alerta de meta compartida
    LaunchedEffect(sharedGoalAchieved) {
        if (sharedGoalAchieved && !goalAchieved) {
            showGoalAchievedAlert = true
            delay(3000)
            showGoalAchievedAlert = false
            onResetSharedGoal() // USAR EL CALLBACK
        }
    }

    // Agregar después de LaunchedEffect(sharedGoalAchieved):
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
            if (updated) {
                Log.d("WearOS", "Estadísticas sincronizadas desde móvil")
            }
        }
    }

    // Enviar estadísticas cada 5 segundos
    LaunchedEffect(isRunning, distance, steps, calories, totalTime) {
        if (isRunning) {
            while (isRunning) {
                delay(3000) // Reducir a 3 segundos para mejor sincronización
                onSendStats(distance, steps, calories, totalTime)
            }
        }
    }

    LaunchedEffect(sharedIsRunning) {
        isRunning = sharedIsRunning
        Log.d("WearOS", "Estado de running sincronizado: $isRunning")
    }

    // CAMBIAR el LaunchedEffect de sharedShouldReset:
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
            onResetSharedGoal() // Reset del flag
            Log.d("WearOS", "Reset ejecutado desde móvil")
        }
    }

    // Calcular valores
    val progress = min(distance / dailyGoal, 1.0f)
    val progressPercentage = (progress * 100).toInt()
    val minutes = totalTime / 60
    val seconds = totalTime % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)
    val pace = if (distance > 0 && totalTime > 0) {
        val paceInSeconds = (totalTime / distance) / 60.0
        val paceMinutes = paceInSeconds.toInt()
        val paceSeconds = ((paceInSeconds - paceMinutes) * 60).toInt()
        "${paceMinutes}'${String.format("%02d", paceSeconds)}\""
    } else {
        "0'00\""
    }

    // Diseño mejorado manteniendo tu estructura
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(darkBlue, deepPurple)
                )
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    if (dragAmount.x < -50 && abs(dragAmount.y) < 100) {
                        onNavigateToSecond()
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "RUNNING TRACKER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp)
            )

            Text(
                text = "Meta diaria ${dailyGoal.toInt()}km - $progressPercentage%",
                color = if (goalAchieved) successColor else accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            // Contenedor para distancia y ritmo cardíaco con indicadores
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // Usamos el CircularProgressIndicator de Wear Compose
                CircularProgressIndicator(
                    progress = progress,
                    modifier = Modifier.size(105.dp),
                    startAngle = 270f,
                    endAngle = 270f + 360f * progress,
                    strokeWidth = 5.dp, // <- Prueba con un valor más pequeño
                    indicatorColor =  successColor,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 15.dp)
                    ) {
                        Text(
                            text = "%.2f".format(distance),
                            color = textColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "km",
                            color = textColor.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }

                    // Mini indicador para ritmo cardíaco
                    Box(
                        modifier = Modifier.size(50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp) // Añade un pequeño padding horizontal
                        ) {
                            Text(
                                text = "❤️",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(end = 2.dp) // Espacio entre el icono y el número
                            )
                            Text(
                                text = "$heartRate",
                                color = textColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1, // Asegura una sola línea
                                softWrap = false, // Evita el salto de línea
                                modifier = Modifier.widthIn(min = 24.dp) // Ancho mínimo para 3 dígitos
                            )
                        }
                    }
                }
            }

            // Estadísticas secundarias en grid 2x2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatItem(
                        value = timeFormatted,
                        label = "Tiempo",
                        highlight = isRunning,
                        highlightColor = accentColor,
                        color = textColor
                    )
                    StatItem(
                        value = pace,
                        label = "Ritmo",
                        color = textColor
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatItem(
                        value = "$calories",
                        label = "Calorías",
                        color = textColor
                    )
                    StatItem(
                        value = "$steps",
                        label = "Pasos",
                        color = textColor
                    )
                }
            }

            // Botones de acción
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Button(
                    onClick = {
                        isRunning = !isRunning
                        onSendRunningState(isRunning)
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isRunning) errorColor else accentColor,
                        contentColor = if (isRunning) textColor else darkBlue
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text(
                        text = if (isRunning) "STOP" else "START",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

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
                        }
                        onSendStats(5.1f, 7500, 450, 1800)
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = warningColor,
                        contentColor = textColor
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text(
                        text = "5km",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

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
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = grayColor,
                        contentColor = textColor
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text(
                        text = "RESET",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showGoalAchievedAlert) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center, // Añade esto para centrado vertical
                    modifier = Modifier
                        .background(
                            color = darkBlue,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )
                        .padding(24.dp)
                        .width(IntrinsicSize.Max) // Asegura que el ancho se ajuste al contenido
                ) {
                    Text(
                        text = "🏆",
                        fontSize = 24.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                        textAlign = TextAlign.Center // Añade alineación de texto para el emoji
                    )
                    Text(
                        text = "¡META ALCANZADA!",
                        color = successColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(), // Ocupa todo el ancho disponible
                        textAlign = TextAlign.Center // Centra el texto horizontalmente
                    )
                    Text(
                        text = "Has completado ${dailyGoal.toInt()}km",
                        color = textColor,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .fillMaxWidth(), // Ocupa todo el ancho disponible
                        textAlign = TextAlign.Center // Centra el texto horizontalmente
                    )
                    Button(
                        onClick = { showGoalAchievedAlert = false },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = successColor,
                            contentColor = darkBlue
                        ),
                        modifier = Modifier.align(Alignment.CenterHorizontally) // Centra el botón
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


// Indicador circular personalizado
@Composable
fun CircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
    color: Color = Color.Blue,
    trackColor: Color = Color.Black
) {
    val sweepAngle = progress * 360

    Canvas(modifier = modifier) {
        // Dibuja el track (fondo)
        drawCircle(
            color = trackColor,
            radius = size.minDimension / 2 - strokeWidth.toPx() / 2,
            style = Stroke(strokeWidth.toPx())
        )

        // Dibuja el progreso
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round),
            size = Size(size.width - strokeWidth.toPx(), size.height - strokeWidth.toPx()),
            topLeft = Offset(strokeWidth.toPx() / 2, strokeWidth.toPx() / 2)
        )
    }
}

@Composable
fun StatItem(
    value: String,
    label: String,
    highlight: Boolean = false,
    highlightColor: Color = Color(0xFF00E0FF),
    color: Color = Color.White
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = if (highlight) highlightColor else color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = label,
            color = color.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
    }
}

// CORRECCIÓN 4: Renombrar SecondScreen para evitar conflicto
@Composable
fun SecondScreenWear(onNavigateBack: () -> Unit) {
    val darkBlue = Color(0xFF0F1C3F)
    val deepPurple = Color(0xFF3A1D6E)
    val accentColor = Color(0xFF00E0FF)
    val textColor = Color.White
    val cardColor = Color(0xFF1E2A5A)
    val successColor = Color(0xFF00FF88)

    // Estado local para los kilómetros diarios (ejemplo)
    val dailyKilometers = remember {
        mapOf(
            "Lunes" to 3.2f,
            "Martes" to 5.1f,
            "Miércoles" to 2.8f
        )
    }

    RunningTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(darkBlue, deepPurple)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            TimeText(
                modifier = Modifier.align(Alignment.TopCenter),
                timeTextStyle = MaterialTheme.typography.caption1.copy(color = textColor)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Título con emoji
                Text(
                    text = "📊",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = "Registro Semanal",
                    color = accentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Cards de kilómetros por día
                dailyKilometers.forEach { (day, km) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(
                                color = cardColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Día de la semana
                            Text(
                                text = day,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            // Kilómetros con indicador de meta
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${String.format("%.1f", km)} km",
                                    color = if (km >= 5.0f) successColor else textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (km >= 5.0f) {
                                    Text(
                                        text = " 🏆",
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Estadísticas de resumen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            color = cardColor.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📈 Resumen",
                            color = accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${String.format("%.1f", dailyKilometers.values.sum())}",
                                    color = textColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Total km",
                                    color = textColor.copy(alpha = 0.7f),
                                    fontSize = 8.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${String.format("%.1f", dailyKilometers.values.average())}",
                                    color = textColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Promedio",
                                    color = textColor.copy(alpha = 0.7f),
                                    fontSize = 8.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${dailyKilometers.values.count { it >= 5.0f }}",
                                    color = successColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Metas",
                                    color = textColor.copy(alpha = 0.7f),
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Instrucciones de navegación
                Text(
                    text = "Toca el botón para regresar",
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Botón de regreso (única forma de navegar)
                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = accentColor,
                        contentColor = darkBlue
                    ),
                    modifier = Modifier.size(width = 80.dp, height = 32.dp)
                ) {
                    Text(
                        text = "← Volver",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}


@Preview
@Composable
fun DefaultPreview() {
    WearApp()
}