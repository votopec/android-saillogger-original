package com.ibsailing.saillogger

import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties

data class DurableLogConfig(
    val startTimestamp: Long,
    val boatNo: Int,
    val teamName: String,
    val updateInterval: Int,
    val deviceName: String,
    val androidSdk: Int,
    val isLoggingHeel: Boolean,
    val isLoggingPitch: Boolean,
    val isLoggingHeading: Boolean,
    val isLoggingAltitude: Boolean,
    val isLoggingMagnetometer: Boolean,
)

data class DurableLogSnapshot(
    val activeFile: File,
    val finalFile: File,
    val metadataFile: File,
    val rowsWritten: Long,
    val gpsRowsWritten: Long,
    val lastTimestamp: Long,
)

data class RecoveredLog(
    val interruptedFileName: String,
    val recoveredFile: File,
)

class DurableLogWriter(
    private val rootDir: File,
    private val config: DurableLogConfig,
    private val syncEveryRows: Int = DEFAULT_SYNC_EVERY_ROWS,
) : Closeable {

    private val baseName = getBaseFileName(config.startTimestamp, config.boatNo)
    private val activeFile = File(rootDir, "$baseName$IN_PROGRESS_SUFFIX")
    private val finalFile = File(rootDir, "$baseName.csv")
    private val metadataFile = File(rootDir, "$baseName$METADATA_SUFFIX")

    private var outputStream: FileOutputStream? = null
    private var writer: BufferedWriter? = null
    private var rowsWritten = 0L
    private var gpsRowsWritten = 0L
    private var lastTimestamp = 0L
    private var started = false

    fun start() {
        if (started) return
        rootDir.mkdirs()
        val isNewFile = !activeFile.exists() || activeFile.length() == 0L
        outputStream = FileOutputStream(activeFile, true)
        writer = BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))
        started = true

        if (isNewFile) {
            writerOrThrow().write(buildHeader(config))
            writerOrThrow().flush()
            syncToDisk()
        }
        writeMetadata(status = STATUS_IN_PROGRESS)
    }

    fun appendRow(csvLine: String, hasGps: Boolean, timestamp: Long) {
        ensureStarted()
        writerOrThrow().append(csvLine).append('\n')
        rowsWritten++
        if (hasGps) gpsRowsWritten++
        if (timestamp > 0L) lastTimestamp = timestamp

        if (rowsWritten % syncEveryRows == 0L) {
            flush(force = true)
        } else {
            writerOrThrow().flush()
        }
        writeMetadata(status = STATUS_IN_PROGRESS)
    }

    fun flush(force: Boolean = false) {
        if (!started) return
        writerOrThrow().flush()
        if (force) {
            syncToDisk()
        }
    }

    fun finish(): File {
        flush(force = true)
        close()
        writeMetadata(status = STATUS_FINISHED)
        if (finalFile.exists()) {
            finalFile.delete()
        }
        if (!activeFile.renameTo(finalFile)) {
            throw IllegalStateException("Unable to finalize log file ${activeFile.name}")
        }
        writeMetadata(status = STATUS_FINISHED)
        return finalFile
    }

    fun snapshot(): DurableLogSnapshot =
        DurableLogSnapshot(activeFile, finalFile, metadataFile, rowsWritten, gpsRowsWritten, lastTimestamp)

    override fun close() {
        writer?.flush()
        writer?.close()
        writer = null
        outputStream = null
        started = false
    }

    private fun ensureStarted() {
        if (!started) start()
    }

    private fun writerOrThrow(): BufferedWriter =
        writer ?: throw IllegalStateException("DurableLogWriter has not been started")

    private fun syncToDisk() {
        outputStream?.fd?.sync()
    }

    private fun writeMetadata(status: String) {
        val props = Properties()
        props["status"] = status
        props["baseName"] = baseName
        props["activeFile"] = activeFile.name
        props["finalFile"] = finalFile.name
        props["teamName"] = config.teamName
        props["boatNo"] = config.boatNo.toString()
        props["startTimestamp"] = config.startTimestamp.toString()
        props["updateInterval"] = config.updateInterval.toString()
        props["rowsWritten"] = rowsWritten.toString()
        props["gpsRowsWritten"] = gpsRowsWritten.toString()
        props["lastTimestamp"] = lastTimestamp.toString()
        props["updatedAt"] = System.currentTimeMillis().toString()

        FileOutputStream(metadataFile, false).use {
            props.store(it, "SailLogger recording session")
            it.fd.sync()
        }
    }

    companion object {
        const val IN_PROGRESS_SUFFIX = ".inprogress.csv"
        const val METADATA_SUFFIX = ".session.properties"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_FINISHED = "finished"
        const val STATUS_RECOVERED = "recovered"
        private const val DEFAULT_SYNC_EVERY_ROWS = 20

        fun buildHeader(config: DurableLogConfig): String {
            val builder = StringBuilder()
            builder.append("#Team:${config.teamName}\n")
            builder.append("#BoatNo:${config.boatNo}\n")
            builder.append("#Source:SailLoggerRaw\n")
            builder.append("#Interval:${config.updateInterval}\n")
            builder.append("#Device:${config.deviceName}\n")
            builder.append("#Android:${config.androidSdk}\n")
            builder.append("Timestamp,Longitude,Latitude,SOG,COG")
            if (config.isLoggingHeel) builder.append(",heel")
            if (config.isLoggingPitch) builder.append(",pitch")
            if (config.isLoggingHeading) builder.append(",heading")
            if (config.isLoggingAltitude) builder.append(",altitude")
            if (config.isLoggingMagnetometer) builder.append(",magnetx,magnety,magnetz")
            builder.append('\n')
            return builder.toString()
        }

        fun getBaseFileName(timeStamp: Long, boatNo: Int): String {
            val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeStamp), ZoneId.of("UTC"))
            return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")) +
                "_Boat" +
                String.format(Locale.US, "%03d", boatNo)
        }

        fun findRecoverableLogs(rootDir: File): List<File> =
            rootDir.listFiles { file ->
                file.isFile && file.name.endsWith(IN_PROGRESS_SUFFIX)
            }?.sortedBy { it.name } ?: emptyList()

        fun recoverInterruptedLogs(rootDir: File): List<RecoveredLog> =
            findRecoverableLogs(rootDir).mapNotNull { activeFile ->
                val baseName = activeFile.name.removeSuffix(IN_PROGRESS_SUFFIX)
                val targetFile = nextAvailableRecoveredFile(rootDir, baseName)
                if (!activeFile.renameTo(targetFile)) {
                    return@mapNotNull null
                }

                writeRecoveryMetadata(rootDir, baseName, activeFile.name, targetFile.name)
                RecoveredLog(activeFile.name, targetFile)
            }

        private fun nextAvailableRecoveredFile(rootDir: File, baseName: String): File {
            val normalFinalFile = File(rootDir, "$baseName.csv")
            if (!normalFinalFile.exists()) {
                return normalFinalFile
            }

            var index = 1
            while (true) {
                val suffix = if (index == 1) "_Recovered" else "_Recovered_$index"
                val candidate = File(rootDir, "$baseName$suffix.csv")
                if (!candidate.exists()) {
                    return candidate
                }
                index++
            }
        }

        private fun writeRecoveryMetadata(
            rootDir: File,
            baseName: String,
            interruptedFileName: String,
            recoveredFileName: String,
        ) {
            val metadataFile = File(rootDir, "$baseName$METADATA_SUFFIX")
            val props = Properties()
            if (metadataFile.exists()) {
                FileInputStream(metadataFile).use { props.load(it) }
            }

            props["status"] = STATUS_RECOVERED
            props["baseName"] = baseName
            props["activeFile"] = interruptedFileName
            props["finalFile"] = recoveredFileName
            props["recoveredAt"] = System.currentTimeMillis().toString()
            props["updatedAt"] = System.currentTimeMillis().toString()

            FileOutputStream(metadataFile, false).use {
                props.store(it, "SailLogger recovered recording session")
                it.fd.sync()
            }
        }
    }
}
