package com.datorio.controller

import com.datorio.model.dto.*
import com.datorio.repository.UserRepository
import com.datorio.service.TemplateService
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import org.springframework.security.access.prepost.PreAuthorize

@RestController
@RequestMapping("/templates")
class TemplateController(
    private val templateService: TemplateService,
    private val userRepository: UserRepository
) {
    private fun getUserId(auth: Authentication): Long =
        userRepository.findByUsername(auth.name)
            .orElseThrow { NoSuchElementException("User not found") }.id

    // ─── Gallery ───

    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @GetMapping
    fun list(
        @RequestParam(required = false) category: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): ResponseEntity<List<TemplateListItem>> =
        ResponseEntity.ok(templateService.listTemplates(category, PageRequest.of(page, size)))

    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @GetMapping("/categories")
    fun categories(): ResponseEntity<List<String>> =
        ResponseEntity.ok(templateService.getCategories())

    @PreAuthorize("hasAuthority('REPORT_EDIT')")
    @PutMapping("/{id}/meta")
    fun updateMeta(
        @PathVariable id: Long,
        @RequestBody request: TemplateUpdateRequest
    ): ResponseEntity<Void> {
        templateService.updateTemplateMeta(id, request)
        return ResponseEntity.ok().build()
    }

    @PreAuthorize("hasAuthority('REPORT_EDIT')")
    @PostMapping("/{reportId}/mark")
    fun markAsTemplate(
        @PathVariable reportId: Long,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) preview: String?
    ): ResponseEntity<TemplateListItem> =
        ResponseEntity.ok(templateService.markAsTemplate(reportId, category, preview))

    @PreAuthorize("hasAuthority('REPORT_EDIT')")
    @PostMapping("/{reportId}/unmark")
    fun unmarkAsTemplate(@PathVariable reportId: Long): ResponseEntity<Void> {
        templateService.unmarkAsTemplate(reportId)
        return ResponseEntity.noContent().build()
    }

    // ─── JSON Export / Import ───

    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @GetMapping("/export/{reportId}")
    fun exportReport(@PathVariable reportId: Long): ResponseEntity<ReportExportConfig> =
        ResponseEntity.ok(templateService.exportReport(reportId))

    @PreAuthorize("hasAuthority('REPORT_EDIT')")
    @PostMapping("/import")
    fun importReport(
        @RequestBody request: ImportReportRequest,
        auth: Authentication
    ): ResponseEntity<ImportResult> =
        ResponseEntity.ok(templateService.importReport(request, getUserId(auth)))
}
