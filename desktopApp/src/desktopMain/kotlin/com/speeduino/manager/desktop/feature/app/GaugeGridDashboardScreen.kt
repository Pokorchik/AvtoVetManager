package com.speeduino.manager.desktop.feature.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.speeduino.manager.desktop.ConnectionState
import com.speeduino.manager.desktop.LocalStrings
import com.speeduino.manager.desktop.ui.KioskPanelCard
import io.ecucore.EcuLiveData
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun Float.toSp() = TextUnit(this, TextUnitType.Sp)

/**
 * Tuning-grid dashboard: a row of small analog (needle) gauges for the core
 * sensors, plus a rolling RPM trend chart underneath - the "Сетка тюнинга +
 * график" concept with needle indicators the user picked.
 */
private const val HISTORY_LENGTH = 90

@Composable
internal fun GaugeGridDashboardScreen(liveData: EcuLiveData?, connectionState: ConnectionState) {
    val strings = LocalStrings.current
    val rpmHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(liveData) {
        val rpm = liveData?.rpm?.toFloat() ?: 0f
        rpmHistory.add(rpm)
        if (rpmHistory.size > HISTORY_LENGTH) rpmHistory.removeAt(0)
    }

    val rpm = liveData?.rpm ?: 0
    val mapKpa = liveData?.mapPressure ?: 100
    val clt = liveData?.coolantTemp ?: 0
    val advance = liveData?.advance ?: 0
    val battery = liveData?.batteryVoltage ?: 0.0

    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!connectionState.isConnected) {
            Text(
                strings["label.noData"],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiniGauge(
                    "Угол", advance.toFloat(), -10f, 50f, "°", 10f, MaterialTheme.colorScheme.secondary,
                    Modifier.weight(1f).fillMaxWidth(),
                )
                MiniGauge(
                    "°C", clt.toFloat(), -40f, 150f, "°C", 50f, MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f).fillMaxWidth(),
                )
            }
            MiniGauge(
                "RPM", rpm.toFloat(), 0f, 7000f, "об/мин", 1000f, MaterialTheme.colorScheme.primary,
                Modifier.weight(1.4f).fillMaxHeight(),
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiniGauge(
                    "кПа", mapKpa.toFloat(), 0f, 100f, "kPa", 20f, MaterialTheme.colorScheme.tertiary,
                    Modifier.weight(1f).fillMaxWidth(),
                )
                MiniGauge(
                    "вольт", battery.toFloat(), 8f, 16f, "V", 2f, MaterialTheme.colorScheme.tertiary,
                    Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }

        KioskPanelCard(strings["route.dashboard"] + " · RPM") {
            TrendChart(
                values = rpmHistory,
                yMax = 7000f,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
        }
    }
}

@Composable
private fun MiniGauge(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    unit: String,
    tickStep: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val face = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = minOf(size.width, size.height) * 0.40f
            val startAngle = 135f
            val sweep = 270f
            val ratio = ((value - min) / (max - min)).coerceIn(0f, 1f)

            drawCircle(color = face, radius = radius, center = Offset(cx, cy))

            val majorValues = generateSequence(min) { v -> (v + tickStep).takeIf { it <= max + 0.01f } }.toList()
            val tickLabelStyle = TextStyle(color = tickColor, fontSize = (radius * 0.115f).toSp(), textAlign = TextAlign.Center)
            majorValues.forEachIndexed { index, majorValue ->
                val majorRatio = ((majorValue - min) / (max - min)).coerceIn(0f, 1f)
                val a = (startAngle + sweep * majorRatio) * PI.toFloat() / 180f
                val outer = radius * 0.92f
                drawLine(
                    color = tickColor,
                    start = Offset(cx + cos(a) * (outer - radius * 0.12f), cy + sin(a) * (outer - radius * 0.12f)),
                    end = Offset(cx + cos(a) * outer, cy + sin(a) * outer),
                    strokeWidth = 2.2f,
                    cap = StrokeCap.Round,
                )
                val labelText = majorValue.toInt().toString()
                val measured = textMeasurer.measure(text = labelText, style = tickLabelStyle)
                val labelDist = radius * 1.14f
                val lx = cx + cos(a) * labelDist
                val ly = cy + sin(a) * labelDist
                drawText(textLayoutResult = measured, topLeft = Offset(lx - measured.size.width / 2f, ly - measured.size.height / 2f))

                if (index < majorValues.lastIndex) {
                    val midValue = (majorValue + majorValues[index + 1]) / 2f
                    val midRatio = ((midValue - min) / (max - min)).coerceIn(0f, 1f)
                    val ma = (startAngle + sweep * midRatio) * PI.toFloat() / 180f
                    val mOuter = radius * 0.92f
                    val mInner = mOuter - radius * 0.06f
                    drawLine(
                        color = tickColor,
                        start = Offset(cx + cos(ma) * mInner, cy + sin(ma) * mInner),
                        end = Offset(cx + cos(ma) * mOuter, cy + sin(ma) * mOuter),
                        strokeWidth = 1.1f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            val needleAngle = (startAngle + sweep * ratio) * PI.toFloat() / 180f
            drawLine(
                color = color,
                start = Offset(cx - cos(needleAngle) * radius * 0.18f, cy - sin(needleAngle) * radius * 0.18f),
                end = Offset(cx + cos(needleAngle) * radius * 0.8f, cy + sin(needleAngle) * radius * 0.8f),
                strokeWidth = radius * 0.07f,
                cap = StrokeCap.Round,
            )
            drawCircle(color = color, radius = radius * 0.09f, center = Offset(cx, cy))

            val valueText = if (unit == "V") String.format("%.1f", value) else value.toInt().toString()
            val measured = textMeasurer.measure(
                text = valueText,
                style = TextStyle(color = tickColor, fontSize = (radius * 0.2f).toSp(), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
            drawText(textLayoutResult = measured, topLeft = Offset(cx - measured.size.width / 2f, cy + radius * 0.32f))

            val labelMeasured = textMeasurer.measure(
                text = label,
                style = TextStyle(color = tickColor, fontSize = (radius * 0.2f).toSp(), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
            drawText(textLayoutResult = labelMeasured, topLeft = Offset(cx - labelMeasured.size.width / 2f, cy - radius * 0.56f))
        }
    }
}

@Composable
private fun TrendChart(values: List<Float>, yMax: Float, color: Color, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val padding = 6f
        fun sy(v: Float) = size.height - padding - (v / yMax).coerceIn(0f, 1f) * (size.height - 2 * padding)
        val stepX = (size.width - 2 * padding) / (HISTORY_LENGTH - 1)
        val startIndex = HISTORY_LENGTH - values.size

        for (i in 0..4) {
            val y = padding + (size.height - 2 * padding) * i / 4
            drawLine(gridColor, Offset(padding, y), Offset(size.width - padding, y), strokeWidth = 1f)
        }

        val path = Path().apply {
            values.forEachIndexed { i, v ->
                val x = padding + (startIndex + i) * stepX
                val y = sy(v)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(path, color = color, style = Stroke(width = 2.5f))
    }
}
