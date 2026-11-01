package com.datorio.scheduling

import com.datorio.service.DataAlertService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Checks the data alerts that are due every minute; the alert's own interval decides which are due. */
@Component
class AlertScheduler(private val alertService: DataAlertService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 60_000, initialDelay = 90_000)
    fun checkAlerts() {
        try {
            alertService.checkDueAlerts()
        } catch (e: Exception) {
            log.error("Alert check loop failed: {}", e.message)
        }
    }
}
