package dev.voltra

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringWriter
import java.util.TimeZone

class CsvExportTest {
    @Test
    fun writesHeaderAndRowsWithOptionalFieldsEmpty() {
        val writer = StringWriter()
        val observations = listOf(
            Observation(
                time = 1_700_000_000_000L,
                level = 85,
                plugged = true,
                charging = true,
                counter = 3_400_000,
                temperature = 31.5f,
                screenOn = true,
                plugType = 1,
                currentMicroamps = 1_500_000,
                voltageMv = 4210,
            ),
            Observation(
                time = 1_700_000_060_000L,
                level = 84,
                plugged = false,
                charging = false,
                counter = null,
                temperature = null,
                screenOn = null,
                plugType = null,
                currentMicroamps = null,
                voltageMv = null,
            ),
        )

        CsvExport.write(writer, observations)
        val lines = writer.toString().trimEnd('\n').split('\n')

        assertEquals(3, lines.size)
        assertEquals(CsvExport.HEADER, lines[0])
        assertEquals("1700000000000,85,true,true,3400000,31.5,true,1,1500000,4210", lines[1])
        assertEquals("1700000060000,84,false,false,,,,,,", lines[2])
    }

    @Test
    fun fileNameIncludesIsoDate() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            // 2026-11-21T00:00:00Z
            val epochMs = 1_795_219_200_000L
            assertEquals("voltra-history-2026-11-21.csv", CsvExport.fileName(epochMs))
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
