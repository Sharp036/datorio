package com.datorio.scheduling

import org.springframework.scheduling.support.CronExpression

/**
 * Schedules are written in the usual five-field cron ("0 9 * * *" = every day at 09:00 UTC).
 * Spring's CronExpression needs six fields with the seconds first, so a five-field expression
 * gets "0 " in front; a six-field expression is taken as Spring writes it.
 */
object CronExpressions {

    fun normalize(expression: String): String {
        val parts = expression.trim().split(Regex("\\s+"))
        return when (parts.size) {
            5 -> "0 " + parts.joinToString(" ")
            6 -> parts.joinToString(" ")
            else -> throw IllegalArgumentException(
                "Invalid cron expression '$expression': expected 5 fields (minute hour day-of-month month day-of-week)"
            )
        }
    }

    fun parse(expression: String): CronExpression {
        val normalized = normalize(expression)
        require(CronExpression.isValidExpression(normalized)) { "Invalid cron expression '$expression'" }
        return CronExpression.parse(normalized)
    }
}
