package com.speeduino.manager.desktop.tools

import io.ecucore.EcuClient
import io.ecucore.EcuLiveData
import io.ecucore.connection.EcuSerialConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Temporary CLI probe: connects to a real ECU over USB serial and prints live data to stdout.
 * Not wired into the app UI/navigation - run directly via the liveDataCli Gradle task.
 */
fun main(args: Array<String>): Unit = runBlocking {
    val port = args.getOrNull(0) ?: "COM8"
    val durationSec = (args.getOrNull(1) ?: "15").toInt()

    println("Connecting to $port ...")

    val connection = EcuSerialConnection(portDescriptor = port, baudRate = 115200)

    val client = EcuClient(
        connection = connection,
        onDataReceived = { data: EcuLiveData ->
            println(
                "RPM=${data.rpm} MAP=${data.mapPressure}kPa TPS=${data.tps}% " +
                    "CLT=${data.coolantTemp}C IAT=${data.intakeTemp}C " +
                    "VBat=${data.batteryVoltage}V Advance=${data.advance} O2=${data.o2} " +
                    "running=${data.isEngineRunning}"
            )
        },
        onConnectionStateChanged = { connected ->
            println(if (connected) "Connected." else "Disconnected.")
        },
        onError = { message ->
            println("ERROR: $message")
        },
    )

    try {
        withTimeout(30_000) { client.connect() }
        println("Handshake OK, firmware=${client.getFirmwareInfoCached()}")

        if (durationSec > 0) {
            client.startLiveDataStream()
            delay(durationSec * 1000L)
            client.stopLiveDataStream()
        }

        println("\n--- Engine Constants ---")
        runCatching { withTimeout(10_000) { client.readEngineConstants() } }
            .onSuccess { println(it) }
            .onFailure { println("FAILED: ${it.message}") }

        println("\n--- Trigger Settings ---")
        runCatching { withTimeout(10_000) { client.readTriggerSettings() } }
            .onSuccess { println(it) }
            .onFailure { println("FAILED: ${it.message}") }

        println("\n--- VE Table ---")
        runCatching { withTimeout(10_000) { client.readVeTable(0) } }
            .onSuccess { printTable(it.rpmBins, it.loadBins, it.values) }
            .onFailure { println("FAILED: ${it.message}") }

        println("\n--- Ignition Table ---")
        runCatching { withTimeout(10_000) { client.readIgnitionTable(0) } }
            .onSuccess { printTable(it.rpmBins, it.loadBins, it.values) }
            .onFailure { println("FAILED: ${it.message}") }

        println("\n--- AFR Table ---")
        runCatching { withTimeout(10_000) { client.readAfrTable() } }
            .onSuccess { printTable(it.rpmBins, it.loadBins, it.values) }
            .onFailure { println("FAILED: ${it.message}") }
    } catch (e: Exception) {
        println("FAILED: ${e.message}")
        e.printStackTrace()
    } finally {
        connection.disconnect()
        println("Done.")
    }
}

private fun printTable(rpmBins: List<Int>, loadBins: List<Int>, values: List<List<Int>>) {
    println("RPM bins: $rpmBins")
    println("Load bins: $loadBins")
    values.forEachIndexed { row, cols ->
        println("load=${loadBins.getOrNull(row)}: ${cols.joinToString(" ")}")
    }
}
