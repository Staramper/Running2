package com.example.running.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.running.presentation.theme.RunningTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

// Importaciones para Wearable Data Layer
import com.google.android.gms.wearable.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log
// CORRECCIÓN 1: Importar correctamente las extensiones de tasks
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.ExecutionException

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private lateinit var dataClient: DataClient

    // Agregar esta variable
    private var updateSharedState: ((Float, Int, Int, Int, Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar el cliente de datos
        dataClient = Wearable.getDataClient(this)

        // AGREGAR ESTA LÍNEA
        checkWearableConnection()

        setContent {
            WearApp(
                onGoalAchieved = { sendGoalAchievedToMobile() },
                onUpdateSharedState = { updateFn ->
                    updateSharedState = updateFn
                }
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

                        // Usar la función de actualización en lugar de runOnUiThread
                        updateSharedState?.invoke(0f, 0, 0, 0, true)
                    }
                    "/running_stats" -> {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        Log.d("WearOS", "Recibidas estadísticas desde móvil")

                        // Usar la función de actualización
                        updateSharedState?.invoke(
                            dataMap.getFloat("distance", 0f),
                            dataMap.getInt("steps", 0),
                            dataMap.getInt("calories", 0),
                            dataMap.getInt("time", 0),
                            false
                        )
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

    private fun sendStatsToMobile(distance: Float, steps: Int, calories: Int, time: Int) {
        lifecycleScope.launch {
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

    private fun sendGoalAchievedToMobile() {
        lifecycleScope.launch {
            try {
                val putDataRequest = PutDataMapRequest.create("/goal_achieved").run {
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                    dataMap.putString("source", "wear")
                    dataMap.putString("message", "Meta completada desde WearOS")
                    asPutDataRequest()
                }

                // CORRECCIÓN 2: Usar Tasks.await en lugar de .await()
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
    onUpdateSharedState: ((Float, Int, Int, Int, Boolean) -> Unit) -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf("main") }

    // MOVER ESTAS VARIABLES AQUÍ (desde la clase MainActivity)
    var sharedDistance by remember { mutableStateOf(0f) }
    var sharedSteps by remember { mutableStateOf(0) }
    var sharedCalories by remember { mutableStateOf(0) }
    var sharedTime by remember { mutableStateOf(0) }
    var sharedGoalAchieved by remember { mutableStateOf(false) }

    // Función para enviar estadísticas
    val sendStatsToMobile = { distance: Float, steps: Int, calories: Int, time: Int ->
        // Esta función se implementará más adelante
    }

    // Configurar la función de actualización
    LaunchedEffect(Unit) {
        onUpdateSharedState { distance, steps, calories, time, goalAchieved ->
            sharedDistance = distance
            sharedSteps = steps
            sharedCalories = calories
            sharedTime = time
            sharedGoalAchieved = goalAchieved
        }
    }

    when (currentScreen) {
        "main" -> MainScreen(
            onNavigateToSecond = { currentScreen = "second" },
            onGoalAchieved = onGoalAchieved,
            sharedDistance = sharedDistance,
            sharedSteps = sharedSteps,
            sharedCalories = sharedCalories,
            sharedTime = sharedTime,
            sharedGoalAchieved = sharedGoalAchieved,
            onSendStats = sendStatsToMobile
        )
        "second" -> SecondScreenWear(onNavigateBack = { currentScreen = "main" })
    }
}

@Composable
fun MainScreen(
    onNavigateToSecond: () -> Unit,
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
    var totalTime by remember { mutableStateOf(0) } // en segundos
    var calories by remember { mutableStateOf(0) }
    var heartRate by remember { mutableStateOf(90) }
    var steps by remember { mutableStateOf(0) }
    var showGoalAchievedAlert by remember { mutableStateOf(false) }
    var goalAchieved by remember { mutableStateOf(false) }

    // Meta diaria en km
    val dailyGoal = 5.0f

    // Simulación de datos en tiempo real
    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000) // Actualizar cada segundo

            // Incrementar distancia (aproximadamente 0.003 km por segundo = 10.8 km/h)
            distance += 0.003f

            // Incrementar tiempo
            totalTime += 1

            // Incrementar calorías (aproximadamente 1 por segundo)
            calories += 1

            // Variar ritmo cardíaco (entre 140-160 durante ejercicio)
            heartRate = (140..160).random()

            // Incrementar pasos (aproximadamente 2 pasos por segundo)
            steps += 2

            // Verificar si se alcanzó la meta diaria
            if (distance >= dailyGoal && !goalAchieved) {
                goalAchieved = true
                showGoalAchievedAlert = true
                onGoalAchieved() // Enviar notificación al móvil
            }
        }
    }

    // Resetear ritmo cardíaco cuando no está corriendo
    LaunchedEffect(isRunning) {
        if (!isRunning && heartRate > 100) {
            // Simular que el ritmo cardíaco baja gradualmente
            while (heartRate > 90 && !isRunning) {
                delay(2000)
                heartRate = maxOf(90, heartRate - 5)
            }
        }
    }

    // AGREGAR ESTE LaunchedEffect DESPUÉS DE LAS OTRAS LaunchedEffect
    LaunchedEffect(sharedGoalAchieved) {
        if (sharedGoalAchieved && !goalAchieved) {
            showGoalAchievedAlert = true
            delay(3000) // Mostrar por 3 segundos
            showGoalAchievedAlert = false
        }
    }

    // AGREGAR ESTE LaunchedEffect PARA ENVIAR ESTADÍSTICAS CADA 5 SEGUNDOS
        LaunchedEffect(isRunning, distance, steps, calories, totalTime) {
            if (isRunning) {
                while (isRunning) {
                    delay(5000) // Enviar cada 5 segundos
                    onSendStats(distance, steps, calories, totalTime)
                }
            }
        }

    // Calcular valores derivados
    val progress = min(distance / dailyGoal, 1.0f)
    val progressPercentage = (progress * 100).toInt()
    val dailyGoalText = "Meta diaria ${dailyGoal.toInt()}km - $progressPercentage%"

    // Formatear tiempo
    val minutes = totalTime / 60
    val seconds = totalTime % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    // Calcular ritmo (min/km)
    val pace = if (distance > 0 && totalTime > 0) {
        val paceInSeconds = (totalTime / distance) / 60.0
        val paceMinutes = paceInSeconds.toInt()
        val paceSeconds = ((paceInSeconds - paceMinutes) * 60).toInt()
        "${paceMinutes}'${String.format("%02d", paceSeconds)}\""
    } else {
        "0'00\""
    }

    // Colores personalizados
    val darkBlue = Color(0xFF0F1C3F)
    val deepPurple = Color(0xFF3A1D6E)
    val accentColor = Color(0xFF00E0FF)
    val textColor = Color.White
    val successColor = Color(0xFF00FF88)

    RunningTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        // Detectar deslizamiento hacia la izquierda
                        if (dragAmount.x < -50 && abs(dragAmount.y) < 100) {
                            onNavigateToSecond()
                        }
                    }
                }
        ) {
            // Contenido principal
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(darkBlue, deepPurple)
                        )
                    )
                    .padding(0.dp),
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
                    // Label "Meta diaria"
                    Text(
                        text = dailyGoalText,
                        color = if (goalAchieved) successColor else accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp)
                    )

                    // Indicador circular de progreso con ritmo cardíaco
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(95.dp),
                            progress = progress,
                            startAngle = 0f,
                            endAngle = 360f,
                            indicatorColor = if (goalAchieved) successColor else accentColor,
                            trackColor = textColor.copy(alpha = 0.1f),
                            strokeWidth = 6.dp
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Distancia con "km" al lado
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "%.2f".format(distance),
                                    color = textColor,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "km",
                                    color = textColor.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }

                            // Valor del ritmo cardíaco
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 1.dp)
                            ) {
                                Text(
                                    text = "❤️",
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(end = 2.dp)
                                )
                                Text(
                                    text = "$heartRate",
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Estadísticas secundarias (4 columnas)
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 6.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = timeFormatted,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Tiempo",
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 8.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = pace,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Ritmo",
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 8.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$calories",
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Calorías",
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 8.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$steps",
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Pasos",
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 8.sp
                            )
                        }
                    }

                    // Botones de acción - 3 botones en fila
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Button(
                            onClick = { isRunning = !isRunning },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (isRunning) Color.Red else accentColor,
                                contentColor = if (isRunning) Color.White else darkBlue
                            ),
                            modifier = Modifier.size(45.dp)
                        ) {
                            Text(
                                text = if (isRunning) "Stop" else "Start",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Botón de simulación rápida para emulador
                        Button(
                            onClick = {
                                // Simular completar 5km instantáneamente
                                distance = 5.1f // Un poco más de 5km para asegurar que se active la alerta
                                totalTime = 1800 // 30 minutos en segundos
                                calories = 450 // Calorías aproximadas para 5km
                                heartRate = 155 // Ritmo cardíaco elevado
                                steps = 7500 // Pasos aproximados para 5km
                                if (!goalAchieved) {
                                    goalAchieved = true
                                    showGoalAchievedAlert = true
                                    onGoalAchieved() // Enviar notificación al móvil
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFFFF6B35), // Color naranja
                                contentColor = Color.White
                            ),
                            modifier = Modifier.size(45.dp)
                        ) {
                            Text(
                                text = "5km",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Botón de reset
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
                                backgroundColor = Color.Gray,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.size(45.dp)
                        ) {
                            Text(
                                text = "Reset",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Alerta de meta alcanzada
            if (showGoalAchievedAlert) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Card/Container para la alerta
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .background(
                                color = darkBlue,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🏆",
                                fontSize = 18.sp,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                            Text(
                                text = "¡Meta Alcanzada!",
                                color = successColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                            )
                            Text(
                                text = "Has completado ${dailyGoal.toInt()}km!",
                                color = textColor,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Button(
                                onClick = { showGoalAchievedAlert = false },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = successColor,
                                    contentColor = darkBlue
                                )
                            ) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// CORRECCIÓN 4: Renombrar SecondScreen para evitar conflicto
@Composable
fun SecondScreenWear(onNavigateBack: () -> Unit) {
    // Implementación básica de la segunda pantalla
    val darkBlue = Color(0xFF0F1C3F)
    val deepPurple = Color(0xFF3A1D6E)
    val textColor = Color.White

    RunningTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(darkBlue, deepPurple)
                    )
                )
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        // Detectar deslizamiento hacia la derecha para volver
                        if (dragAmount.x > 50 && abs(dragAmount.y) < 100) {
                            onNavigateBack()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "📊",
                    fontSize = 40.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Segunda Pantalla",
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Desliza hacia la derecha para volver",
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp()
}