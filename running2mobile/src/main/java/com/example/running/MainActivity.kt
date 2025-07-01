package com.example.running

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.running.ui.theme.RunningTheme
import kotlinx.coroutines.delay
import kotlin.math.min

// Importaciones para Wearable Data Layer
import com.google.android.gms.wearable.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log
// CORRECCIÓN: Usar Tasks.await en lugar de kotlinx.coroutines.tasks.await
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.ExecutionException

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private lateinit var dataClient: DataClient

    // POR ESTO:
    internal var _sharedDistance = mutableStateOf(0f)
    internal var _sharedSteps = mutableStateOf(0)
    internal var _sharedCalories = mutableStateOf(0)
    internal var _sharedTime = mutableStateOf(0)
    internal var _sharedGoalAchieved = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inicializar el cliente de datos
        dataClient = Wearable.getDataClient(this)

        // AGREGAR ESTA LÍNEA
        checkWearableConnection()

        setContent {
            RunningTheme {
                RunningMobileApp(
                    onGoalAchieved = { sendGoalAchievedToWear() },
                    activity = this@MainActivity
                )
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

                        // Actualizar estado compartido
                        runOnUiThread {
                            _sharedGoalAchieved.value = true
                        }
                    }
                    "/running_stats" -> {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        Log.d("Mobile", "Recibidas estadísticas desde WearOS")

                        // Actualizar estadísticas compartidas
                        runOnUiThread {
                            _sharedDistance.value = dataMap.getFloat("distance", 0f)
                            _sharedSteps.value = dataMap.getInt("steps", 0)
                            _sharedCalories.value = dataMap.getInt("calories", 0)
                            _sharedTime.value = dataMap.getInt("time", 0)
                        }
                    }
                }
            }
        }
    }

    private fun checkWearableConnection() {
        lifecycleScope.launch {
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
        lifecycleScope.launch {
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
                Log.d("Mobile", "Estadísticas enviadas a WearOS: ${result.uri}")
            } catch (e: Exception) {
                Log.e("Mobile", "Error enviando estadísticas a WearOS", e)
            }
        }
    }

    private fun sendGoalAchievedToWear() {
        lifecycleScope.launch {
            try {
                val putDataRequest = PutDataMapRequest.create("/goal_achieved").run {
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                    dataMap.putString("source", "mobile")
                    dataMap.putString("message", "Meta completada desde móvil")
                    asPutDataRequest()
                }

                // CORRECCIÓN: Usar Tasks.await() en lugar de .await()
                val result = Tasks.await(dataClient.putDataItem(putDataRequest))
                Log.d("Mobile", "Datos enviados a WearOS: ${result.uri}")
            } catch (e: ExecutionException) {
                Log.e("Mobile", "Error enviando datos a WearOS", e)
            } catch (e: InterruptedException) {
                Log.e("Mobile", "Error enviando datos a WearOS", e)
            } catch (e: Exception) {
                Log.e("Mobile", "Error enviando datos a WearOS", e)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningMobileApp(
    onGoalAchieved: () -> Unit = {},
    activity: MainActivity? = null
) {
    var currentScreen by remember { mutableStateOf("main") }

    // Colores personalizados
    val darkBlue = Color(0xFF0F1C3F)
    val deepPurple = Color(0xFF3A1D6E)
    val accentColor = Color(0xFF00E0FF)
    val successColor = Color(0xFF00FF88)

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = darkBlue,
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    icon = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🏃",
                                fontSize = 24.sp
                            )
                            Text(
                                text = "Running",
                                fontSize = 10.sp,
                                color = if (currentScreen == "main") accentColor else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    },
                    selected = currentScreen == "main",
                    onClick = { currentScreen = "main" },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accentColor,
                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                        indicatorColor = accentColor.copy(alpha = 0.2f)
                    )
                )

                NavigationBarItem(
                    icon = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "📊",
                                fontSize = 24.sp
                            )
                            Text(
                                text = "Historial",
                                fontSize = 10.sp,
                                color = if (currentScreen == "history") accentColor else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    },
                    selected = currentScreen == "history",
                    onClick = { currentScreen = "history" },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accentColor,
                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                        indicatorColor = accentColor.copy(alpha = 0.2f)
                    )
                )
            }
        }
    ) { paddingValues ->
        when (currentScreen) {
            "main" -> MainScreen(
                paddingValues = paddingValues,
                darkBlue = darkBlue,
                deepPurple = deepPurple,
                accentColor = accentColor,
                successColor = successColor,
                onGoalAchieved = onGoalAchieved,
                sharedDistance = activity?._sharedDistance?.value ?: 0f,
                sharedSteps = activity?._sharedSteps?.value ?: 0,
                sharedCalories = activity?._sharedCalories?.value ?: 0,
                sharedTime = activity?._sharedTime?.value ?: 0,
                sharedGoalAchieved = activity?._sharedGoalAchieved?.value ?: false,
                onSendStats = { distance, steps, calories, time ->
                    activity?.sendStatsToWear(distance, steps, calories, time)
                }
            )
            "history" -> HistoryScreen(
                paddingValues = paddingValues,
                darkBlue = darkBlue,
                deepPurple = deepPurple,
                accentColor = accentColor,
                successColor = successColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    paddingValues: PaddingValues,
    darkBlue: Color,
    deepPurple: Color,
    accentColor: Color,
    successColor: Color,
    onGoalAchieved: () -> Unit = {},
    // AGREGAR ESTOS PARÁMETROS
    sharedDistance: Float = 0f,
    sharedSteps: Int = 0,
    sharedCalories: Int = 0,
    sharedTime: Int = 0,
    sharedGoalAchieved: Boolean = false,
    onSendStats: (Float, Int, Int, Int) -> Unit = { _, _, _, _ -> }
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
                onGoalAchieved() // Enviar notificación al WearOS
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

    // LaunchedEffect para mostrar alerta cuando se reciba notificación
    LaunchedEffect(sharedGoalAchieved) {
        if (sharedGoalAchieved && !goalAchieved) {
            showGoalAchievedAlert = true
            delay(3000) // Mostrar por 3 segundos
            showGoalAchievedAlert = false
        }
    }

    // LaunchedEffect para enviar estadísticas cada 5 segundos
        LaunchedEffect(isRunning, distance, steps, calories, totalTime) {
            if (isRunning) {
                while (isRunning) {
                    delay(5000) // Enviar cada 5 segundos
                    onSendStats(distance, steps, calories, totalTime)
                }
            }
        }

    // LaunchedEffect para mostrar estadísticas sincronizadas
        LaunchedEffect(sharedDistance, sharedSteps, sharedCalories, sharedTime) {
            // Opcional: Puedes decidir si quieres mostrar las estadísticas compartidas
            // o mantener las locales. Aquí las combinamos:
            if (sharedDistance > distance && !isRunning) {
                distance = sharedDistance
            }
            if (sharedSteps > steps && !isRunning) {
                steps = sharedSteps
            }
            if (sharedCalories > calories && !isRunning) {
                calories = sharedCalories
            }
            if (sharedTime > totalTime && !isRunning) {
                totalTime = sharedTime
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(darkBlue, deepPurple)
                )
            )
            .padding(paddingValues)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "🏃‍♂️ Running Tracker",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp),
                textAlign = TextAlign.Center
            )

            // Card de Meta Diaria
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = darkBlue.copy(alpha = 0.8f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Meta Diaria ${dailyGoal.toInt()}km",
                        color = if (goalAchieved) successColor else accentColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "$progressPercentage% completado",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Indicador de progreso circular grande
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(200.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            color = if (goalAchieved) successColor else accentColor,
                            strokeWidth = 12.dp,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "%.2f".format(distance),
                                    color = Color.White,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "km",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                                )
                            }

                            if (isRunning) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = "❤️",
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = " $heartRate bpm",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Estadísticas detalladas
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = darkBlue.copy(alpha = 0.6f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📊 Estadísticas",
                        color = accentColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )

                    // Grid de estadísticas 2x2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatCard(
                            icon = "⏰",
                            value = timeFormatted,
                            label = "Tiempo",
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        StatCard(
                            icon = "🏃",
                            value = pace,
                            label = "Ritmo/km",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatCard(
                            icon = "🔥",
                            value = "$calories",
                            label = "Calorías",
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        StatCard(
                            icon = "👣",
                            value = "$steps",
                            label = "Pasos",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Controles
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = darkBlue.copy(alpha = 0.6f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎮 Controles",
                        color = accentColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Botón Start/Stop
                        Button(
                            onClick = { isRunning = !isRunning },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) Color.Red else successColor,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isRunning) "⏸️" else "▶️",
                                    fontSize = 24.sp
                                )
                                Text(
                                    text = if (isRunning) "Stop" else "Start",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Botón simulación 5km
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
                                    onGoalAchieved() // Enviar notificación al WearOS
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6B35),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🚀",
                                    fontSize = 24.sp
                                )
                                Text(
                                    text = "5km",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Botón Reset
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
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Gray,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🔄",
                                    fontSize = 24.sp
                                )
                                Text(
                                    text = "Reset",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Alerta de meta alcanzada
        if (showGoalAchievedAlert) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = darkBlue
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🏆",
                            fontSize = 48.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            text = "¡Meta Alcanzada!",
                            color = successColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "¡Has completado ${dailyGoal.toInt()}km hoy!",
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                        Button(
                            onClick = { showGoalAchievedAlert = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = successColor,
                                contentColor = darkBlue
                            )
                        ) {
                            Text(
                                "¡Genial!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    icon: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Columna izquierda (icono y label)
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = icon,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            // Columna derecha (valor)
            Text(
                text = value,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    paddingValues: PaddingValues,
    darkBlue: Color,
    deepPurple: Color,
    accentColor: Color,
    successColor: Color
) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(darkBlue, deepPurple)
                )
            )
            .padding(paddingValues)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "📊 Historial Semanal",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp),
                textAlign = TextAlign.Center
            )

            // Resumen estadístico
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = darkBlue.copy(alpha = 0.8f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📈 Resumen Semanal",
                        color = accentColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${String.format("%.1f", dailyKilometers.values.sum())}",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Total km",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${String.format("%.1f", dailyKilometers.values.average())}",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Promedio",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${dailyKilometers.values.count { it >= 5.0f }}",
                                color = successColor,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Metas",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Lista de días
            dailyKilometers.forEach { (day, km) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = darkBlue.copy(alpha = 0.6f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = day,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Start
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${String.format("%.1f", km)} km",
                                color = if (km >= 5.0f) successColor else Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End
                            )

                            if (km >= 5.0f) {
                                Text(
                                    text = " 🏆",
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RunningMobileAppPreview() {
    RunningTheme {
        RunningMobileApp(activity = null)
    }
}