package com.speeduino.manager.desktop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import com.speeduino.manager.desktop.ui.KioskFeatureCard
import com.speeduino.manager.desktop.ui.KioskPanelCard
import com.speeduino.manager.desktop.ui.KioskScreenScaffold
import com.speeduino.manager.desktop.ui.chooseOpenFile

@Composable
internal fun LogsEcuToolsScreenDesktop(
    onOpenConnection: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onOpenLogViewer: () -> Unit,
    onOpenRealTimeMonitor: () -> Unit,
    onOpenLogAnalyzer: () -> Unit,
    onOpenBeforeAfter: () -> Unit,
    onOpenVirtualDyno: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistoricalLogViewer: (String) -> Unit,
    onOpenRestorePoints: () -> Unit = {},
    onOpenLogMap: (String) -> Unit = {}
) {
    val strings = LocalStrings.current
    KioskScreenScaffold(
        title = strings["route.tools"],
        subtitle = strings["label.toolsSubtitle"]
    ) {
        KioskPanelCard(strings["route.connection"]) {
            KioskFeatureCard(strings["route.connection"], strings["label.toolsConnectionDesc"], icon = Icons.Default.Cable, large = true, onClick = onOpenConnection)
            KioskFeatureCard(strings["label.wifiTcp"], strings["label.toolsConnectionSettingsDesc"], icon = Icons.Default.Wifi, large = true, onClick = onOpenConnectionSettings)
        }
        KioskPanelCard(strings["label.logsEcuToolsTitle"]) {
            KioskFeatureCard(strings["route.realTimeMonitor"], strings["label.realtimeUtilityDesc"], icon = Icons.Default.MonitorHeart, large = true, onClick = onOpenRealTimeMonitor)
            KioskFeatureCard(strings["route.logViewer"], strings["label.logViewerUtilitySummary"], icon = Icons.Default.Description, large = true, onClick = onOpenLogViewer)
            KioskFeatureCard(
                strings["label.logViewerOpenCsvAction"],
                strings["label.logViewerUtilityDesc"],
                icon = Icons.Default.FolderOpen,
                large = true,
                onClick = {
                    chooseOpenFile(strings["label.logViewerOpenCsvTitle"])?.absolutePath?.let(onOpenHistoricalLogViewer)
                }
            )
        }
        KioskPanelCard(strings["label.toolsAnalysisSection"]) {
            KioskFeatureCard(
                strings["log_map_title"],
                strings["log_map_open_action"],
                icon = Icons.Default.FolderOpen,
                large = true,
                onClick = { chooseOpenFile(strings["label.logViewerOpenCsvTitle"])?.absolutePath?.let(onOpenLogMap) }
            )
            KioskFeatureCard(strings["route.logAnalyzer"], strings["label.toolsLogAnalyzerDesc"], icon = Icons.Default.Analytics, large = true, onClick = onOpenLogAnalyzer)
            KioskFeatureCard(strings["route.beforeAfter"], strings["label.toolsBeforeAfterDesc"], icon = Icons.Default.CompareArrows, large = true, onClick = onOpenBeforeAfter)
            KioskFeatureCard(strings["route.virtualDyno"], strings["label.toolsVirtualDynoDesc"], icon = Icons.Default.Speed, large = true, onClick = onOpenVirtualDyno)
        }
        KioskPanelCard(strings["nav.sectionMore"]) {
            KioskFeatureCard(strings["restore_points_title"], strings["restore_points_description"], icon = Icons.Default.FolderOpen, large = true, onClick = onOpenRestorePoints)
            KioskFeatureCard(strings["app.settingsTitle"], icon = Icons.Default.Settings, large = true, onClick = onOpenSettings)
        }
    }
}
