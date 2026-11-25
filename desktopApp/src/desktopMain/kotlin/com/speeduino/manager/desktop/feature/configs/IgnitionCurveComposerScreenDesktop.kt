package com.speeduino.manager.desktop.feature.configs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.sp
import com.speeduino.manager.desktop.DesktopSpeeduinoController
import com.speeduino.manager.desktop.LocalStrings
import com.speeduino.manager.desktop.ui.KioskPanelCard
import com.speeduino.manager.desktop.ui.KioskScreenScaffold
import io.ecucore.model.IgnitionTable
import io.ecucore.shared.formatDecimal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

private fun Float.toSp() = TextUnit(this, TextUnitType.Sp)

/**
 * Builds the 2D Speeduino/MS ignition table (RPM x MAP) from two 123ignition-style curves -
 * base advance + an RPM curve + a MAP/vacuum curve - the same approach as the standalone
 * web composer prototype, now wired to the real serial connection: axes come from the
 * currently loaded ignition table (so the write can never desync from the ECU's own bins),
 * and "Write to ECU" goes through the existing saveIgnitionTable()/WriteSafetyGate path.
 */
private val CURVE_MIN_DEGREES = -10.0
private val CURVE_MAX_DEGREES = 40.0

private fun interp(curve: List<Pair<Int, Int>>, x: Double): Double {
    val pts = curve.sortedBy { it.first }
    if (pts.isEmpty()) return 0.0
    if (x <= pts.first().first) return pts.first().second.toDouble()
    if (x >= pts.last().first) return pts.last().second.toDouble()
    for (i in 0 until pts.size - 1) {
        val (x0, y0) = pts[i]
        val (x1, y1) = pts[i + 1]
        if (x in x0.toDouble()..x1.toDouble()) {
            if (x1 == x0) return y0.toDouble()
            val t = (x - x0) / (x1 - x0)
            return y0 + t * (y1 - y0)
        }
    }
    return pts.last().second.toDouble()
}

/** Rounds a span into a "nice" tick step (1/2/5 x 10^n) so grid lines land on round numbers. */
private fun niceStep(range: Double, targetTicks: Int = 5): Double {
    if (range <= 0) return 1.0
    val rough = range / targetTicks
    val mag = Math.pow(10.0, Math.floor(Math.log10(rough)))
    val norm = rough / mag
    val step = when {
        norm < 1.5 -> 1.0
        norm < 3.5 -> 2.0
        norm < 7.5 -> 5.0
        else -> 10.0
    }
    return (step * mag).coerceAtLeast(1.0)
}

@Composable
internal fun IgnitionCurveComposerScreenDesktop(controller: DesktopSpeeduinoController) {
    val strings = LocalStrings.current
    val liveData by controller.liveData.collectAsState()
    val table by controller.ignitionTableState(1).collectAsState()
    val lastError by controller.lastError.collectAsState()

    LaunchedEffect(Unit) {
        if (table == null) controller.loadIgnitionTable(1)
    }

    var base by remember { mutableStateOf(10) }
    var rpmCurve by remember {
        mutableStateOf(
            listOf(0 to 0, 800 to 0, 1500 to 6, 2000 to 10, 2500 to 14, 3000 to 16, 4000 to 16, 6000 to 16)
        )
    }
    var mapCurve by remember {
        mutableStateOf(listOf(20 to 10, 40 to 10, 60 to 6, 80 to 2, 100 to 0))
    }

    fun angleAt(rpm: Double, mapKpa: Double): Double =
        (base + interp(rpmCurve, rpm) + interp(mapCurve, mapKpa)).coerceIn(CURVE_MIN_DEGREES, CURVE_MAX_DEGREES)

    val liveRpm = liveData?.rpm ?: 0
    val liveMapKpa = liveData?.mapPressure ?: 100
    val liveVoltage = liveData?.batteryVoltage ?: 0.0
    val liveAngle = round(angleAt(liveRpm.toDouble(), liveMapKpa.toDouble())).toInt()

    val currentTable = table
    val isMapBased = currentTable == null || currentTable.loadType == IgnitionTable.LoadType.MAP

    KioskScreenScaffold(
        title = strings["route.ignitionCurveComposer"],
        subtitle = strings["label.curveComposerSubtitle"],
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            KioskPanelCard(strings["label.curveComposerLivePanel"]) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MiniTachometer(
                        rpm = liveRpm,
                        maxRpm = ((currentTable?.rpmBins?.maxOrNull() ?: 6500) + 500).coerceAtLeast(4000),
                        modifier = Modifier.size(150.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DigitalReadout(strings["label.curveComposerVoltage"], formatDecimal(liveVoltage, 1), "V", MaterialTheme.colorScheme.tertiary)
                        DigitalReadout(strings["label.curveComposerAngle"], liveAngle.toString(), "°BTDC", MaterialTheme.colorScheme.primary)
                        DigitalReadout(strings["label.curveComposerPressure"], liveMapKpa.toString(), "kPa", MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            if (!isMapBased) {
                Text(
                    text = strings["label.curveComposerTpsWarning"],
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    CurveEditorCard(
                        title = strings["label.curveComposerRpmCurve"],
                        xLabel = "RPM",
                        yLabel = "°",
                        color = MaterialTheme.colorScheme.primary,
                        points = rpmCurve,
                        onPointsChange = { rpmCurve = it },
                        currentX = liveRpm.toDouble()
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    CurveEditorCard(
                        title = strings["label.curveComposerMapCurve"],
                        xLabel = "kPa",
                        yLabel = "°",
                        color = MaterialTheme.colorScheme.secondary,
                        points = mapCurve,
                        onPointsChange = { mapCurve = it },
                        currentX = liveMapKpa.toDouble()
                    )
                }
            }

            OutlinedTextField(
                value = base.toString(),
                onValueChange = { it.toIntOrNull()?.let { v -> base = v } },
                label = { Text(strings["label.curveComposerBase"]) },
                modifier = Modifier.width(140.dp),
                singleLine = true
            )

            if (currentTable != null) {
                KioskPanelCard(strings["label.curveComposerTablePreview"]) {
                    IgnitionTablePreview(
                        rpmBins = currentTable.rpmBins,
                        loadBins = currentTable.loadBins,
                        loadScale = currentTable.loadScale,
                        angleAt = ::angleAt
                    )
                }

                FilledTonalButton(
                    onClick = {
                        val rawValues = currentTable.loadBins.map { loadRaw ->
                            val mapKpa = loadRaw * currentTable.loadScale
                            currentTable.rpmBins.map { rpm ->
                                val deg = angleAt(rpm.toDouble(), mapKpa)
                                round(deg / currentTable.valueScale).toInt()
                            }
                        }
                        controller.saveIgnitionTable(currentTable.copy(values = rawValues), mapIndex = 1)
                    }
                ) {
                    Text(strings["label.curveComposerWrite"])
                }
            } else {
                Text(strings["label.curveComposerNoTable"], style = MaterialTheme.typography.labelMedium)
            }

            if (lastError != null) {
                Text(lastError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DigitalReadout(label: String, value: String, unit: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CurveEditorCard(
    title: String,
    xLabel: String,
    yLabel: String,
    color: Color,
    points: List<Pair<Int, Int>>,
    onPointsChange: (List<Pair<Int, Int>>) -> Unit,
    currentX: Double? = null
) {
    KioskPanelCard(title) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CurveChart(
                points = points,
                color = color,
                xLabel = xLabel,
                yLabel = yLabel,
                currentX = currentX,
                modifier = Modifier.fillMaxWidth().height(180.dp)
            )
            points.forEachIndexed { index, (x, y) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = x.toString(),
                        onValueChange = { s -> s.toIntOrNull()?.let { v -> onPointsChange(points.toMutableList().also { it[index] = v to y }) } },
                        label = { Text(xLabel) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = y.toString(),
                        onValueChange = { s -> s.toIntOrNull()?.let { v -> onPointsChange(points.toMutableList().also { it[index] = x to v }) } },
                        label = { Text(yLabel) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(
                        onClick = { if (points.size > 2) onPointsChange(points.toMutableList().also { it.removeAt(index) }) },
                        enabled = points.size > 2
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            }
            FilledTonalButton(onClick = {
                val lastX = points.maxOfOrNull { it.first } ?: 0
                onPointsChange(points + ((lastX + 250) to 0))
            }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CurveChart(
    points: List<Pair<Int, Int>>,
    color: Color,
    xLabel: String,
    yLabel: String,
    currentX: Double? = null,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val sorted = points.sortedBy { it.first }
    Canvas(modifier = modifier) {
        if (sorted.isEmpty()) return@Canvas
        val leftPad = 34f
        val bottomPad = 32f
        val topPad = 10f
        val rightPad = 10f
        val xMin = sorted.first().first.toDouble()
        val xMax = sorted.last().first.toDouble().let { if (it <= xMin) xMin + 1.0 else it }
        val yMin = (sorted.minOf { it.second } - 2).toDouble()
        val yMax = (sorted.maxOf { it.second } + 2).toDouble().let { if (it <= yMin) yMin + 4.0 else it }
        fun sx(x: Double) = leftPad + ((x - xMin) / (xMax - xMin) * (size.width - leftPad - rightPad)).toFloat()
        fun sy(y: Double) = size.height - bottomPad - ((y - yMin) / (yMax - yMin) * (size.height - topPad - bottomPad)).toFloat()

        fun labelStyle() = TextStyle(color = axisColor, fontSize = 9.sp, textAlign = TextAlign.Center)

        // Y grid (degrees) - whole-number ticks
        val yStep = niceStep(yMax - yMin, 4)
        var yTick = Math.ceil(yMin / yStep) * yStep
        while (yTick <= yMax) {
            val py = sy(yTick)
            drawLine(gridColor, Offset(leftPad, py), Offset(size.width - rightPad, py), strokeWidth = 1f)
            val label = textMeasurer.measure(text = yTick.toInt().toString(), style = labelStyle())
            drawText(label, topLeft = Offset(leftPad - label.size.width - 4f, py - label.size.height / 2f))
            yTick += yStep
        }

        // X grid (RPM / kPa) - whole-number ticks
        val xStep = niceStep(xMax - xMin, 5)
        var xTick = Math.ceil(xMin / xStep) * xStep
        while (xTick <= xMax) {
            val px = sx(xTick)
            drawLine(gridColor, Offset(px, topPad), Offset(px, size.height - bottomPad), strokeWidth = 1f)
            val label = textMeasurer.measure(text = xTick.toInt().toString(), style = labelStyle())
            drawText(label, topLeft = Offset(px - label.size.width / 2f, size.height - bottomPad + 4f))
            xTick += xStep
        }

        // Axis lines + unit labels
        drawLine(axisColor, Offset(leftPad, size.height - bottomPad), Offset(size.width - rightPad, size.height - bottomPad), strokeWidth = 1.5f)
        drawLine(axisColor, Offset(leftPad, topPad), Offset(leftPad, size.height - bottomPad), strokeWidth = 1.5f)
        val xUnit = textMeasurer.measure(text = xLabel, style = labelStyle())
        drawText(xUnit, topLeft = Offset(size.width - rightPad - xUnit.size.width, size.height - 10f))
        val yUnit = textMeasurer.measure(text = yLabel, style = labelStyle())
        drawText(yUnit, topLeft = Offset(leftPad + 2f, topPad))

        if (currentX != null && currentX in xMin..xMax) {
            val px = sx(currentX)
            drawLine(
                color = Color.Red,
                start = Offset(px, topPad),
                end = Offset(px, size.height - bottomPad),
                strokeWidth = 2f,
            )
        }

        val path = androidx.compose.ui.graphics.Path().apply {
            sorted.forEachIndexed { i, (x, y) ->
                if (i == 0) moveTo(sx(x.toDouble()), sy(y.toDouble())) else lineTo(sx(x.toDouble()), sy(y.toDouble()))
            }
        }
        drawPath(path, color = color, style = Stroke(width = 2.5f))
        sorted.forEach { (x, y) ->
            drawCircle(color = color, radius = 4f, center = Offset(sx(x.toDouble()), sy(y.toDouble())))
        }
    }
}

@Composable
private fun IgnitionTablePreview(
    rpmBins: List<Int>,
    loadBins: List<Int>,
    loadScale: Double,
    angleAt: (Double, Double) -> Double
) {
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.fillMaxWidth().height(340.dp)) {
        val rows = loadBins.size
        val cols = rpmBins.size
        if (rows == 0 || cols == 0) return@Canvas
        val leftPad = 34f
        val bottomPad = 22f
        val gridW = size.width - leftPad
        val gridH = size.height - bottomPad
        val cellW = gridW / cols
        val cellH = gridH / rows
        val fontSize = (minOf(cellW, cellH) * 0.42f).coerceIn(7f, 13f)
        val axisStyle = TextStyle(color = axisColor, fontSize = 8.sp)

        for (r in 0 until rows) {
            val mapKpa = loadBins[r] * loadScale
            for (c in 0 until cols) {
                val deg = angleAt(rpmBins[c].toDouble(), mapKpa)
                val ratio = ((deg - CURVE_MIN_DEGREES) / (CURVE_MAX_DEGREES - CURVE_MIN_DEGREES)).coerceIn(0.0, 1.0)
                val hue = (205f - ratio.toFloat() * 190f)
                val lightness = 0.55f + ratio.toFloat() * 0.25f
                val color = Color.hsv(hue, 0.55f, lightness)
                val top = gridH - (r + 1) * cellH
                val left = leftPad + c * cellW
                drawRect(color = color, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(cellW, cellH))

                val textColor = if (lightness > 0.62f) Color.Black else Color.White
                val measured = textMeasurer.measure(
                    text = round(deg).toInt().toString(),
                    style = TextStyle(color = textColor, fontSize = fontSize.toSp(), textAlign = TextAlign.Center),
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(left + cellW / 2f - measured.size.width / 2f, top + cellH / 2f - measured.size.height / 2f),
                )
            }

            val rowLabel = textMeasurer.measure(text = loadBins[r].toString(), style = axisStyle)
            val rowTop = gridH - (r + 1) * cellH
            drawText(rowLabel, topLeft = Offset(leftPad - rowLabel.size.width - 4f, rowTop + cellH / 2f - rowLabel.size.height / 2f))
        }

        for (c in 0 until cols) {
            val colLabel = textMeasurer.measure(text = rpmBins[c].toString(), style = axisStyle)
            val colLeft = leftPad + c * cellW
            drawText(colLabel, topLeft = Offset(colLeft + cellW / 2f - colLabel.size.width / 2f, gridH + 4f))
        }

        val kpaUnit = textMeasurer.measure(text = "kPa", style = axisStyle)
        drawText(kpaUnit, topLeft = Offset(0f, 0f))
        val rpmUnit = textMeasurer.measure(text = "RPM", style = axisStyle)
        drawText(rpmUnit, topLeft = Offset(size.width - rpmUnit.size.width, size.height - rpmUnit.size.height))
    }
}

@Composable
private fun MiniTachometer(rpm: Int, maxRpm: Int, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val face = MaterialTheme.colorScheme.surfaceVariant
    val needleColor = MaterialTheme.colorScheme.primary
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) * 0.46f
        val startAngle = 135f
        val sweep = 270f
        val ratio = (rpm.toFloat() / maxRpm).coerceIn(0f, 1f)

        drawCircle(color = face, radius = radius, center = center)

        val steps = (maxRpm / 500).coerceAtLeast(1)
        for (i in 0..steps) {
            val v = i * 500
            val a = (startAngle + sweep * (v.toFloat() / maxRpm)) * PI.toFloat() / 180f
            val isMajor = v % 1000 == 0
            val outer = radius * 0.92f
            val inner = outer - radius * (if (isMajor) 0.12f else 0.06f)
            drawLine(
                color = tickColor,
                start = Offset(center.x + cos(a) * inner, center.y + sin(a) * inner),
                end = Offset(center.x + cos(a) * outer, center.y + sin(a) * outer),
                strokeWidth = if (isMajor) 2.5f else 1.2f,
                cap = StrokeCap.Round
            )
        }

        val needleAngle = (startAngle + sweep * ratio) * PI.toFloat() / 180f
        drawLine(
            color = needleColor,
            start = Offset(center.x - cos(needleAngle) * radius * 0.2f, center.y - sin(needleAngle) * radius * 0.2f),
            end = Offset(center.x + cos(needleAngle) * radius * 0.82f, center.y + sin(needleAngle) * radius * 0.82f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        drawCircle(color = needleColor, radius = 6f, center = center)

        val measured = textMeasurer.measure(
            text = rpm.toString(),
            style = TextStyle(color = tickColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        )
        drawText(textLayoutResult = measured, topLeft = Offset(center.x - measured.size.width / 2f, center.y + radius * 0.3f))
    }
}
