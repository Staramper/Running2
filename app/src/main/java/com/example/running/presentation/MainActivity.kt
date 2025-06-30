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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    var currentScreen by remember { mutableStateOf("main") }

    when (currentScreen) {
        "main" -> MainScreen(onNavigateToSecond = { currentScreen = "second" })
        "second" -> SecondScreen(onNavigateBack = { currentScreen = "main" })
    }
}

@Composable
fun MainScreen(onNavigateToSecond: () -> Unit) {
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

                    // Indicador circular de progreso con ritmo cardíaco - MÁS COMPACTO
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(95.dp), // Reducido de 150dp
                            progress = progress,
                            startAngle = 0f,
                            endAngle = 360f,
                            indicatorColor = if (goalAchieved) successColor else accentColor,
                            trackColor = textColor.copy(alpha = 0.1f),
                            strokeWidth = 6.dp // Reducido de 8dp
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Distancia con "km" al lado - MÁS COMPACTO
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "%.2f".format(distance),
                                    color = textColor,
                                    fontSize = 20.sp, // Reducido de 24sp
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "km",
                                    color = textColor.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }

                            // Valor del ritmo cardíaco - MÁS COMPACTO
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
                                    fontSize = 12.sp, // Reducido de 14sp
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Estadísticas secundarias (4 columnas) - MÁS COMPACTAS
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 6.dp) // Reducido padding
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = timeFormatted,
                                color = textColor,
                                fontSize = 12.sp, // Reducido de 14sp
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Tiempo",
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 8.sp // Reducido de 10sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = pace,
                                color = textColor,
                                fontSize = 12.sp, // Reducido de 14sp
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Ritmo",
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 8.sp // Reducido de 10sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$calories",
                                color = textColor,
                                fontSize = 12.sp, // Reducido de 14sp
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Calorías",
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 8.sp // Reducido de 10sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$steps",
                                color = textColor,
                                fontSize = 12.sp, // Reducido de 14sp
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Pasos",
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 8.sp // Reducido de 10sp
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

            // Alerta de meta alcanzada - AHORA ESTÁ AL FINAL PARA QUE SE SUPERPONGA
            if (showGoalAchievedAlert) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)), // Fondo más oscuro
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

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp()
}