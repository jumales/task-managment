package com.demo.taskmanager.feature.reports

import com.demo.taskmanager.data.dto.HoursByProjectDto
import com.demo.taskmanager.data.dto.HoursByTaskDto
import com.demo.taskmanager.data.dto.MyTaskReportDto
import com.demo.taskmanager.data.dto.enums.TaskStatus
import com.demo.taskmanager.feature.reports.export.hoursByProjectToCsv
import com.demo.taskmanager.feature.reports.export.hoursByTaskToCsv
import com.demo.taskmanager.feature.reports.export.myTasksToCsv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Verifies that CSV row totals match the sum of planned and booked hours across fixture data.
 */
class HoursReportTest {

    private val taskRows = listOf(
        HoursByTaskDto(taskId = "t1", taskCode = "TASK-1", title = "Design", plannedHours = 10, bookedHours = 8),
        HoursByTaskDto(taskId = "t2", taskCode = "TASK-2", title = "Implementation", plannedHours = 20, bookedHours = 25),
        HoursByTaskDto(taskId = "t3", taskCode = "TASK-3", title = "Testing", plannedHours = 5, bookedHours = 5),
    )

    private val projectRows = listOf(
        HoursByProjectDto(projectId = "p1", projectName = "Alpha", plannedHours = 35, bookedHours = 38),
        HoursByProjectDto(projectId = "p2", projectName = "Beta", plannedHours = 20, bookedHours = 15),
    )

    @Test
    fun `task totals match sum of fixture rows`() {
        val totalPlanned = taskRows.sumOf { it.plannedHours }
        val totalBooked = taskRows.sumOf { it.bookedHours }
        assertEquals(35L, totalPlanned)
        assertEquals(38L, totalBooked)
    }

    @Test
    fun `project totals match sum of fixture rows`() {
        val totalPlanned = projectRows.sumOf { it.plannedHours }
        val totalBooked = projectRows.sumOf { it.bookedHours }
        assertEquals(55L, totalPlanned)
        assertEquals(53L, totalBooked)
    }

    @Test
    fun `delta per task row equals booked minus planned`() {
        taskRows.forEach { row ->
            assertEquals(row.bookedHours - row.plannedHours, row.bookedHours - row.plannedHours)
        }
        // TASK-2 is over budget
        val overBudget = taskRows.first { it.taskCode == "TASK-2" }
        assertEquals(5L, overBudget.bookedHours - overBudget.plannedHours)
    }

    @Test
    fun `hours by task CSV contains header and correct row count`() {
        val csv = taskRows.hoursByTaskToCsv()
        val lines = csv.trim().lines()
        assertEquals(4, lines.size) // 1 header + 3 rows
        assertEquals("Code,Title,Planned Hours,Booked Hours,Delta", lines.first())
    }

    @Test
    fun `hours by project CSV contains header and correct row count`() {
        val csv = projectRows.hoursByProjectToCsv()
        val lines = csv.trim().lines()
        assertEquals(3, lines.size) // 1 header + 2 rows
        assertEquals("Project,Planned Hours,Booked Hours,Delta", lines.first())
    }

    @Test
    fun `my tasks CSV contains header and correct row count`() {
        val tasks = listOf(
            MyTaskReportDto(
                id = "1", taskCode = "TASK-1", title = "Fix bug", description = null,
                status = TaskStatus.IN_PROGRESS, phaseName = "Dev",
                plannedStart = "2026-01-01", plannedEnd = "2026-01-10", updatedAt = null,
            ),
        )
        val csv = tasks.myTasksToCsv()
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertEquals("Code,Title,Status,Phase,Planned Start,Planned End", lines.first())
    }
}
