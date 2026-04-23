package com.demo.taskmanager.data.api

import com.demo.taskmanager.data.dto.HoursByProjectDto
import com.demo.taskmanager.data.dto.HoursByTaskDto
import com.demo.taskmanager.data.dto.HoursDetailedDto
import com.demo.taskmanager.data.dto.MyTaskReportDto
import com.demo.taskmanager.data.dto.ProjectOpenTaskCountDto
import retrofit2.http.GET
import retrofit2.http.Query

/** Retrofit interface for reporting-service: task and hours reports. */
interface ReportingApi {

    /** Returns tasks assigned to or participated in by the current user, optionally filtered by look-ahead days. */
    @GET("api/v1/reports/my-tasks")
    suspend fun getMyTasks(@Query("days") days: Int? = null): List<MyTaskReportDto>

    /** Planned vs booked hours per task within a project. */
    @GET("api/v1/reports/hours/by-task")
    suspend fun getHoursByTask(@Query("projectId") projectId: String): List<HoursByTaskDto>

    /** Planned vs booked hours aggregated per project. */
    @GET("api/v1/reports/hours/by-project")
    suspend fun getHoursByProject(): List<HoursByProjectDto>

    /** Planned vs booked hours for a task broken down by user and work type. */
    @GET("api/v1/reports/hours/detailed")
    suspend fun getHoursDetailed(@Query("taskId") taskId: String): List<HoursDetailedDto>

    /** Open task counts per project — personal and project-wide totals. */
    @GET("api/v1/reports/tasks/open-by-project")
    suspend fun getOpenTasksByProject(): List<ProjectOpenTaskCountDto>
}
