package com.example.running.presentation

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.running.presentation.theme.RunningTheme

@Composable
fun SecondScreen(onNavigateBack: () -> Unit) {
    // Colores personalizados (mismos que la pantalla principal)
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