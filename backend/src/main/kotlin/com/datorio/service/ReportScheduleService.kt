package com.datorio.service

import com.datorio.model.*
import com.datorio.model.dto.*
import com.datorio.repository.*
import com.datorio.scheduling.CronExpressions
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ReportScheduleService(
    private val scheduleRepo: ReportScheduleRepository,
    private val reportRepo: ReportRepository,
    private val renderService: ReportRenderService,
    private val objectMapper: ObjectMapper,
    private val emailService: EmailService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createSchedule(request: CreateScheduleRequest, username: String): ScheduleResponse {
        require(reportRepo.existsById(request.reportId)) {
            "Report not found: ${request.reportId}"
        }
        val schedule = ReportSchedule(
            reportId = request.reportId,
            cronExpression = validateCron(request.cronExpression),
            parameters = request.parameters,
            outputFormat = request.outputFormat,
            recipients = request.recipients,
            createdBy = null
        )
        val saved = scheduleRepo.save(schedule)
        log.info("Created schedule id={} for report {}, cron='{}'",
            saved.id, saved.reportId, saved.cronExpression)
        return toResponse(saved)
    }

    fun getSchedule(id: Long): ScheduleResponse {
        val schedule = scheduleRepo.findById(id)
            .orElseThrow { IllegalArgumentException("Schedule not found: $id") }
        return toResponse(schedule)
    }

    fun listSchedulesForReport(reportId: Long): List<ScheduleResponse> {
        return scheduleRepo.findByReportId(reportId).map { toResponse(it) }
    }

    fun listActiveSchedules(): List<ScheduleResponse> {
        return scheduleRepo.findActiveSchedules().map { toResponse(it) }
    }

    @Transactional
    fun updateSchedule(id: Long, request: UpdateScheduleRequest): ScheduleResponse {
        val schedule = scheduleRepo.findById(id)
            .orElseThrow { IllegalArgumentException("Schedule not found: $id") }

        request.cronExpression?.let { schedule.cronExpression = validateCron(it) }
        request.isActive?.let { schedule.isActive = it }
        request.parameters?.let { schedule.parameters = it }
        request.outputFormat?.let { schedule.outputFormat = it }
        request.recipients?.let { schedule.recipients = it }
        schedule.updatedAt = Instant.now()

        val saved = scheduleRepo.save(schedule)
        log.info("Updated schedule id={}", saved.id)
        return toResponse(saved)
    }

    @Transactional
    fun toggleSchedule(id: Long): ScheduleResponse {
        val schedule = scheduleRepo.findById(id)
            .orElseThrow { IllegalArgumentException("Schedule not found: $id") }
        schedule.isActive = !schedule.isActive
        schedule.updatedAt = Instant.now()
        val saved = scheduleRepo.save(schedule)
        log.info("Schedule id={} is now {}", saved.id, if (saved.isActive) "ACTIVE" else "PAUSED")
        return toResponse(saved)
    }

    @Transactional
    fun deleteSchedule(id: Long) {
        require(scheduleRepo.existsById(id)) { "Schedule not found: $id" }
        scheduleRepo.deleteById(id)
        log.info("Deleted schedule id={}", id)
    }

    /**
     * Execute a schedule immediately (manual trigger or cron trigger).
     * Renders the report, saves a snapshot, updates schedule status.
     */
    @Transactional
    fun executeSchedule(scheduleId: Long, username: String): ReportSnapshot {
        val schedule = scheduleRepo.findById(scheduleId)
            .orElseThrow { IllegalArgumentException("Schedule not found: $scheduleId") }

        val params: Map<String, Any?> = try {
            if (schedule.parameters == "{}") emptyMap()
            else objectMapper.readValue(schedule.parameters)
        } catch (e: Exception) {
            emptyMap()
        }

        val snapshot = renderService.renderAndSnapshot(
            reportId = schedule.reportId,
            params = params,
            username = username,
            scheduleId = schedule.id,
            outputFormat = schedule.outputFormat
        )

        // Update schedule last-run status
        schedule.lastRunAt = Instant.now()
        if (snapshot.status == "SUCCESS") {
            schedule.lastStatus = ScheduleStatus.SUCCESS
            schedule.lastError = sendToRecipients(schedule, params, username)
            if (schedule.lastError != null) schedule.lastStatus = ScheduleStatus.ERROR
        } else {
            schedule.lastStatus = ScheduleStatus.ERROR
            schedule.lastError = snapshot.errorMessage
        }
        schedule.updatedAt = Instant.now()
        scheduleRepo.save(schedule)

        log.info("Executed schedule id={} for report {}: status={}",
            scheduleId, schedule.reportId, snapshot.status)
        return snapshot
    }

    // ── Helpers ──

    /** The recipients of the schedule get the report in its output format; returns the error text, if any. */
    private fun sendToRecipients(schedule: ReportSchedule, params: Map<String, Any?>, username: String): String? {
        val recipients: List<String> = try {
            objectMapper.readValue(schedule.recipients)
        } catch (_: Exception) { emptyList() }
        if (recipients.isEmpty()) return null
        val format = if (schedule.outputFormat == OutputFormat.JSON) "CSV" else schedule.outputFormat.name
        val result = emailService.sendReportEmail(
            EmailDeliveryRequest(reportId = schedule.reportId, recipients = recipients, format = format, parameters = params),
            username
        )
        return if (result.success) null else result.message
    }

    /** Checks the expression and returns it in the stored five-field form. */
    private fun validateCron(expression: String): String {
        CronExpressions.parse(expression)
        val parts = expression.trim().split(Regex("\\s+"))
        return if (parts.size == 6 && parts[0] == "0") parts.drop(1).joinToString(" ") else parts.joinToString(" ")
    }

    private fun toResponse(s: ReportSchedule): ScheduleResponse {
        val reportName = try {
            reportRepo.findById(s.reportId).orElse(null)?.name
        } catch (_: Exception) { null }

        return ScheduleResponse(
            id = s.id, reportId = s.reportId, reportName = reportName,
            cronExpression = s.cronExpression, isActive = s.isActive,
            parameters = s.parameters, outputFormat = s.outputFormat,
            recipients = s.recipients, lastRunAt = s.lastRunAt,
            lastStatus = s.lastStatus, lastError = s.lastError,
            createdBy = s.createdBy, createdAt = s.createdAt, updatedAt = s.updatedAt
        )
    }
}
