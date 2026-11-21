package dev.voltra

import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExport {
    const val HEADER =
        "timestamp_ms,level_percent,plugged,charging,charge_counter_uAh,temperature_c,device_interactive,plug_type,current_ua,voltage_mv"

    fun fileName(nowMs: Long = System.currentTimeMillis()): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
        return "voltra-history-$date.csv"
    }

    fun formatRow(sample: Observation): String = buildString {
        append(sample.time).append(',')
        append(sample.level).append(',')
        append(sample.plugged).append(',')
        append(sample.charging).append(',')
        sample.counter?.let { append(it) }
        append(',')
        sample.temperature?.let { append(it) }
        append(',')
        sample.screenOn?.let { append(it) }
        append(',')
        sample.plugType?.let { append(it) }
        append(',')
        sample.currentMicroamps?.let { append(it) }
        append(',')
        sample.voltageMv?.let { append(it) }
    }

    fun write(writer: Writer, observations: List<Observation>) {
        writer.append(HEADER).append('\n')
        observations.forEach { sample ->
            writer.append(formatRow(sample)).append('\n')
        }
    }
}
