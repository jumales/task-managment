package com.demo.taskmanager.data.repo

import com.demo.taskmanager.data.api.ReportingApi
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.common.safeApiCall
import com.demo.taskmanager.data.dto.HoursByProjectDto
import com.demo.taskmanager.data.dto.HoursByTaskDto
import com.demo.taskmanager.data.dto.HoursDetailedDto
import com.demo.taskmanager.data.dto.MyTaskReportDto
import com.demo.taskmanager.data.dto.ProjectOpenTaskCountDto
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Delegates all reporting-service calls to [ReportingApi]; caching and mapping added per feature. */
@Singleton
class ReportingRepository @Inject constructor(
    private val api: ReportingApi,
    private val json: Json,
) {

    suspend fun getMyTasks(days: Int? = null): NetworkResult<List<MyTaskReportDto>> =
        safeApiCall(json) { api.getMyTasks(days) }

    suspend fun getHoursByTask(projectId: String? = null): NetworkResult<List<HoursByTaskDto>> =
        safeApiCall(json) { api.getHoursByTask(projectId) }

    suspend fun getHoursByProject(): NetworkResult<List<HoursByProjectDto>> =
        safeApiCall(json) { api.getHoursByProject() }

    suspend fun getHoursDetailed(taskId: String): NetworkResult<List<HoursDetailedDto>> =
        safeApiCall(json) { api.getHoursDetailed(taskId) }

    suspend fun getOpenTasksByProject(): NetworkResult<List<ProjectOpenTaskCountDto>> =
        safeApiCall(json) { api.getOpenTasksByProject() }
}
