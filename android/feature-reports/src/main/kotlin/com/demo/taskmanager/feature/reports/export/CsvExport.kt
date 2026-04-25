package com.demo.taskmanager.feature.reports.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.demo.taskmanager.data.dto.HoursByProjectDto
import com.demo.taskmanager.data.dto.HoursByTaskDto
import com.demo.taskmanager.data.dto.HoursDetailedDto
import com.demo.taskmanager.data.dto.MyTaskReportDto
import com.demo.taskmanager.data.dto.ProjectOpenTaskCountDto
import java.io.File

/** Converts my-tasks report data to a CSV string. */
fun List<MyTaskReportDto>.myTasksToCsv(): String {
    val sb = StringBuilder()
    sb.appendLine("Code,Title,Status,Phase,Planned Start,Planned End")
    for (row in this) {
        sb.appendLine("${row.taskCode},\"${row.title.escapeCsv()}\",${row.status},${row.phaseName},${row.plannedStart ?: ""},${row.plannedEnd ?: ""}")
    }
    return sb.toString()
}

/** Converts hours-by-task report data to a CSV string. */
fun List<HoursByTaskDto>.hoursByTaskToCsv(): String {
    val sb = StringBuilder()
    sb.appendLine("Code,Title,Planned Hours,Booked Hours,Delta")
    for (row in this) {
        sb.appendLine("${row.taskCode},\"${row.title.escapeCsv()}\",${row.plannedHours},${row.bookedHours},${row.bookedHours - row.plannedHours}")
    }
    return sb.toString()
}

/** Converts hours-by-project report data to a CSV string. */
fun List<HoursByProjectDto>.hoursByProjectToCsv(): String {
    val sb = StringBuilder()
    sb.appendLine("Project,Planned Hours,Booked Hours,Delta")
    for (row in this) {
        sb.appendLine("\"${row.projectName.escapeCsv()}\",${row.plannedHours},${row.bookedHours},${row.bookedHours - row.plannedHours}")
    }
    return sb.toString()
}

/** Converts detailed hours data to a CSV string. */
fun List<HoursDetailedDto>.hoursDetailedToCsv(): String {
    val sb = StringBuilder()
    sb.appendLine("User ID,Work Type,Planned Hours,Booked Hours")
    for (row in this) {
        sb.appendLine("${row.userId},${row.workType},${row.plannedHours},${row.bookedHours}")
    }
    return sb.toString()
}

/** Converts open-by-project data to a CSV string. */
fun List<ProjectOpenTaskCountDto>.openByProjectToCsv(): String {
    val sb = StringBuilder()
    sb.appendLine("Project,My Open Tasks,Total Open Tasks")
    for (row in this) {
        sb.appendLine("\"${row.projectName.escapeCsv()}\",${row.myOpenCount},${row.totalOpenCount}")
    }
    return sb.toString()
}

/**
 * Writes [csv] to the app's cache directory and launches the system share chooser.
 * Uses [FileProvider] so that the receiving app can read the file without MANAGE_EXTERNAL_STORAGE.
 */
fun shareCsv(context: Context, csv: String, filename: String = "report.csv") {
    val file = File(context.cacheDir, filename)
    file.writeText(csv)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share $filename"))
}

/** Escapes double-quotes inside a CSV field value. */
private fun String.escapeCsv(): String = replace("\"", "\"\"")
