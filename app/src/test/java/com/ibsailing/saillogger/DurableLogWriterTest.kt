package com.ibsailing.saillogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileInputStream
import java.util.Properties

class DurableLogWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun startWritesHeaderAndInProgressMetadata() {
        val writer = DurableLogWriter(tempFolder.root, testConfig(), syncEveryRows = 1)

        writer.start()
        val snapshot = writer.snapshot()
        writer.close()

        assertTrue(snapshot.activeFile.exists())
        assertTrue(snapshot.metadataFile.exists())

        val text = snapshot.activeFile.readText()
        assertTrue(text.contains("#Team:Test Team"))
        assertTrue(text.contains("#BoatNo:7"))
        assertTrue(text.contains("Timestamp,Longitude,Latitude,SOG,COG,heel,pitch"))

        val metadata = readProperties(snapshot.metadataFile)
        assertEquals(DurableLogWriter.STATUS_IN_PROGRESS, metadata.getProperty("status"))
        assertEquals("0", metadata.getProperty("rowsWritten"))
    }

    @Test
    fun appendRowTracksGpsRowsAndFinalizesFile() {
        val writer = DurableLogWriter(tempFolder.root, testConfig(), syncEveryRows = 1)

        writer.start()
        writer.appendRow("1786601566778,14.4437,45.3281,0.0,0.0,,", hasGps = true, timestamp = 1786601566778)
        writer.appendRow("1786601567000,,,,,0.0,85.25", hasGps = false, timestamp = 1786601567000)
        val finalFile = writer.finish()

        assertTrue(finalFile.exists())
        assertFalse(writer.snapshot().activeFile.exists())

        val rows = finalFile.readLines().filter { it.isNotBlank() }
        assertEquals(9, rows.size)
        assertEquals("1786601566778,14.4437,45.3281,0.0,0.0,,", rows[7])
        assertEquals("1786601567000,,,,,0.0,85.25", rows[8])

        val metadata = readProperties(writer.snapshot().metadataFile)
        assertEquals(DurableLogWriter.STATUS_FINISHED, metadata.getProperty("status"))
        assertEquals("2", metadata.getProperty("rowsWritten"))
        assertEquals("1", metadata.getProperty("gpsRowsWritten"))
        assertEquals("1786601567000", metadata.getProperty("lastTimestamp"))
    }

    @Test
    fun findRecoverableLogsReturnsOnlyInProgressCsvFiles() {
        val first = tempFolder.newFile("2026-08-13_061154_Boat001.inprogress.csv")
        val second = tempFolder.newFile("2026-08-13_061200_Boat001.inprogress.csv")
        tempFolder.newFile("2026-08-13_061154_Boat001.csv")
        tempFolder.newFile("notes.txt")

        assertEquals(listOf(first, second), DurableLogWriter.findRecoverableLogs(tempFolder.root))
    }

    private fun testConfig(): DurableLogConfig =
        DurableLogConfig(
            startTimestamp = 1786601514000,
            boatNo = 7,
            teamName = "Test Team",
            updateInterval = 1000,
            deviceName = "unit test",
            androidSdk = 35,
            isLoggingHeel = true,
            isLoggingPitch = true,
            isLoggingHeading = false,
            isLoggingAltitude = false,
            isLoggingMagnetometer = false,
        )

    private fun readProperties(file: java.io.File): Properties =
        Properties().also { props ->
            FileInputStream(file).use { props.load(it) }
        }
}
