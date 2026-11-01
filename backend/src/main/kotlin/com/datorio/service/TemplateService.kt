package com.datorio.service

import com.datorio.model.*
import com.datorio.model.dto.*
import com.datorio.repository.ReportRepository
import com.datorio.repository.SavedQueryRepository
import com.datorio.model.dto.ContainerRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class TemplateService(
    private val reportRepo: ReportRepository,
    private val reportService: ReportService,
    private val vizService: VisualizationService,
    private val controlService: ControlService,
    private val interactiveService: InteractiveDashboardService,
    private val objectMapper: ObjectMapper,
    private val savedQueryRepository: SavedQueryRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ═══════════════════════════════════════════
    //  Template Gallery
    // ═══════════════════════════════════════════

    fun listTemplates(category: String?, pageable: Pageable): List<TemplateListItem> {
        val page = if (category != null) {
            reportRepo.findByIsTemplateTrueAndTemplateCategory(category, pageable)
        } else {
            reportRepo.findByIsTemplateTrue(pageable)
        }
        return page.content.map { it.toTemplateItem() }
    }

    fun getCategories(): List<String> {
        return reportRepo.findDistinctTemplateCategories()
    }

    @Transactional
    fun updateTemplateMeta(reportId: Long, req: TemplateUpdateRequest) {
        val report = reportRepo.findById(reportId)
            .orElseThrow { NoSuchElementException("Report not found: $reportId") }
        require(report.isTemplate) { "Report $reportId is not a template" }
        req.category?.let { report.templateCategory = it }
        req.preview?.let { report.templatePreview = it }
        req.thumbnailUrl?.let { report.thumbnailUrl = it }
        report.updatedAt = Instant.now()
        reportRepo.save(report)
    }

    @Transactional
    fun markAsTemplate(reportId: Long, category: String?, preview: String?): TemplateListItem {
        val report = reportRepo.findById(reportId)
            .orElseThrow { NoSuchElementException("Report not found: $reportId") }
        report.isTemplate = true
        report.templateCategory = category
        report.templatePreview = preview
        report.updatedAt = Instant.now()
        return reportRepo.save(report).toTemplateItem()
    }

    @Transactional
    fun unmarkAsTemplate(reportId: Long) {
        val report = reportRepo.findById(reportId)
            .orElseThrow { NoSuchElementException("Report not found: $reportId") }
        report.isTemplate = false
        report.templateCategory = null
        report.templatePreview = null
        report.updatedAt = Instant.now()
        reportRepo.save(report)
    }

    // ═══════════════════════════════════════════
    //  JSON Export
    // ═══════════════════════════════════════════

    @Transactional(readOnly = true)
    fun exportReport(reportId: Long): ReportExportConfig {
        val report = reportRepo.findById(reportId)
            .orElseThrow { NoSuchElementException("Report not found: $reportId") }

        // Build a map from widget DB ID -> sortOrder to resolve container child references
        val widgetIdToSortOrder: Map<Long, Int> = report.widgets.associate { it.id to it.sortOrder }

        val containers = vizService.getContainers(reportId).map { c ->
            ContainerExportConfig(
                containerType = c.containerType,
                name = c.name,
                childWidgetSortOrders = c.childWidgetIds.map { group ->
                    group.mapNotNull { widgetIdToSortOrder[it] }
                },
                tabNames = c.tabNames,
                activeTab = c.activeTab,
                sortOrder = c.sortOrder,
                autoDistribute = c.autoDistribute,
                config = objectMapper.writeValueAsString(c.config)
            )
        }

        // NAVIGATE actions open another report of this installation by its id, so they are not exported
        val actions = interactiveService.getActiveActionsForReport(reportId)
            .filter { it.actionType != "NAVIGATE" }
            .map { a ->
                ActionExportConfig(
                    name = a.name, actionType = a.actionType, triggerType = a.triggerType,
                    sourceWidgetSortOrder = a.sourceWidgetId?.let { widgetIdToSortOrder[it] },
                    targetWidgetSortOrders = a.targetWidgetIds?.let { t ->
                        mapWidgetTargets(t) { id -> widgetIdToSortOrder[id]?.toLong() }
                    },
                    sourceField = a.sourceField, targetField = a.targetField, urlTemplate = a.urlTemplate,
                    config = objectMapper.writeValueAsString(a.config)
                )
            }

        return ReportExportConfig(
            name = report.name,
            description = report.description,
            reportType = report.reportType,
            layout = report.layout,
            settings = report.settings,
            category = report.templateCategory,
            parameters = report.parameters.sortedBy { it.sortOrder }.map { p ->
                ParameterExportConfig(
                    name = p.name, label = p.label, paramType = p.paramType,
                    defaultValue = p.defaultValue, isRequired = p.isRequired,
                    sortOrder = p.sortOrder, config = p.config
                )
            },
            widgets = report.widgets.sortedBy { it.sortOrder }.let { sortedWidgets ->
                val widgetIds = sortedWidgets.map { it.id }
                val layersByWidget = interactiveService.getLayersForWidgets(widgetIds)
                val annotationsByWidget = vizService.getAnnotationsForWidgets(widgetIds)
                val tooltipByWidget = vizService.getTooltipConfigs(widgetIds)
                val rulesByWidget = interactiveService.getRulesForWidgets(widgetIds)
                sortedWidgets.map { w ->
                    val layers = (layersByWidget[w.id] ?: emptyList()).map { layer ->
                        ChartLayerExportConfig(
                            name = layer.name, label = layer.label, rawSql = layer.rawSql,
                            chartType = layer.chartType, axis = layer.axis, color = layer.color,
                            opacity = layer.opacity, isVisible = layer.isVisible, sortOrder = layer.sortOrder,
                            seriesConfig = objectMapper.writeValueAsString(layer.seriesConfig),
                            categoryField = layer.categoryField, valueField = layer.valueField,
                            paramMapping = objectMapper.writeValueAsString(layer.paramMapping)
                        )
                    }
                    WidgetExportConfig(
                        widgetType = w.widgetType, title = w.title, body = w.body,
                        // A saved query is an object of this installation: the export carries its SQL instead
                        rawSql = w.rawSql ?: w.queryId?.let { savedQueryRepository.findById(it).orElse(null)?.sqlText },
                        chartConfig = remapToggledWidgets(w.chartConfig, TOGGLE_IDS, TOGGLE_SORT_ORDERS) {
                            widgetIdToSortOrder[it]?.toLong()
                        },
                        position = w.position, style = w.style,
                        paramMapping = w.paramMapping, sortOrder = w.sortOrder,
                        isVisible = w.isVisible, layers = layers,
                        annotations = (annotationsByWidget[w.id] ?: emptyList()).map { a ->
                            AnnotationExportConfig(
                                annotationType = a.annotationType, axis = a.axis, value = a.value,
                                valueEnd = a.valueEnd, label = a.label, color = a.color,
                                lineStyle = a.lineStyle, lineWidth = a.lineWidth, opacity = a.opacity,
                                fillColor = a.fillColor, fillOpacity = a.fillOpacity, position = a.position,
                                fontSize = a.fontSize, isVisible = a.isVisible, sortOrder = a.sortOrder,
                                config = objectMapper.writeValueAsString(a.config)
                            )
                        },
                        tooltip = tooltipByWidget[w.id]?.let { t ->
                            TooltipExportConfig(
                                isEnabled = t.isEnabled, showTitle = t.showTitle, titleField = t.titleField,
                                fields = t.fields, showSparkline = t.showSparkline,
                                sparklineField = t.sparklineField, htmlTemplate = t.htmlTemplate,
                                config = objectMapper.writeValueAsString(t.config)
                            )
                        },
                        visibilityRules = (rulesByWidget[w.id] ?: emptyList()).filter { it.isActive }.map { r ->
                            VisibilityRuleExportConfig(
                                ruleType = r.ruleType, parameterName = r.parameterName,
                                operator = r.operator, expectedValue = r.expectedValue
                            )
                        }
                    )
                }
            },
            containers = containers,
            parameterControls = controlService.getParameterControls(reportId).map { c ->
                ParameterControlExportConfig(
                    parameterName = c.parameterName,
                    controlType = c.controlType,
                    datasourceId = c.datasourceId,
                    optionsQuery = c.optionsQuery,
                    sliderMin = c.sliderMin,
                    sliderMax = c.sliderMax,
                    sliderStep = c.sliderStep,
                    config = objectMapper.writeValueAsString(c.config),
                    sortOrder = c.sortOrder
                )
            },
            actions = actions,
            overlays = interactiveService.getOverlaysForReport(reportId).map { o ->
                OverlayExportConfig(
                    overlayType = o.overlayType, content = o.content,
                    positionX = o.positionX, positionY = o.positionY, width = o.width, height = o.height,
                    opacity = o.opacity, zIndex = o.zIndex, linkUrl = o.linkUrl, isVisible = o.isVisible,
                    style = objectMapper.writeValueAsString(o.style)
                )
            }
        )
    }

    // ═══════════════════════════════════════════
    //  JSON Import
    // ═══════════════════════════════════════════

    @Transactional
    fun importReport(req: ImportReportRequest, userId: Long): ImportResult {
        val cfg = req.config
        val reportName = req.name ?: cfg.name

        val createReq = CreateReportRequest(
            name = reportName,
            description = cfg.description,
            reportType = cfg.reportType,
            layout = cfg.layout,
            settings = cfg.settings,
            isTemplate = req.asTemplate,
            folderId = req.folderId,
            parameters = cfg.parameters.map { p ->
                ReportParameterDto(
                    name = p.name, label = p.label, paramType = p.paramType,
                    defaultValue = p.defaultValue, isRequired = p.isRequired,
                    sortOrder = p.sortOrder, config = p.config
                )
            },
            widgets = cfg.widgets.map { w ->
                CreateWidgetRequest(
                    widgetType = w.widgetType, title = w.title, body = w.body,
                    datasourceId = req.datasourceId,
                    rawSql = w.rawSql, chartConfig = w.chartConfig,
                    position = w.position, style = w.style,
                    paramMapping = w.paramMapping, sortOrder = w.sortOrder,
                    isVisible = w.isVisible
                )
            }
        )

        val created = reportService.createReport(createReq, userId)

        // Build sortOrder -> new widget ID map to resolve container child references
        val sortOrderToWidgetId: Map<Int, Long> = created.widgets.associate { it.sortOrder to it.id }
        cfg.containers.forEach { c ->
            val childIds = c.childWidgetSortOrders.map { group ->
                group.mapNotNull { sortOrderToWidgetId[it] }
            }.filter { it.isNotEmpty() }
            if (childIds.isNotEmpty()) {
                vizService.createContainer(ContainerRequest(
                    reportId = created.id,
                    containerType = c.containerType,
                    name = c.name,
                    childWidgetIds = childIds,
                    tabNames = c.tabNames,
                    activeTab = c.activeTab,
                    autoDistribute = c.autoDistribute,
                    config = readMap(c.config),
                    sortOrder = c.sortOrder
                ))
            }
        }

        // Buttons name the widgets they show or hide by sortOrder in the export; put the new ids back
        created.widgets.forEach { w ->
            val chartConfig = remapToggledWidgets(w.chartConfig, TOGGLE_SORT_ORDERS, TOGGLE_IDS) {
                sortOrderToWidgetId[it.toInt()]
            }
            if (chartConfig != w.chartConfig) {
                reportService.updateWidget(w.id, UpdateWidgetRequest(chartConfig = chartConfig), userId)
            }
        }

        // Recreate parameter controls. If the import request overrides datasourceId,
        // apply it to all controls (matches the widget override behaviour). Controls
        // that don't query a datasource (SLIDER/INPUT/DATE_PICKER) ignore the field.
        cfg.parameterControls.forEach { c ->
            val effectiveDatasourceId = req.datasourceId ?: c.datasourceId
            val configMap: Map<String, Any?> = try {
                objectMapper.readValue(c.config)
            } catch (_: Exception) { emptyMap() }
            controlService.saveParameterControl(
                ParameterControlRequest(
                    reportId = created.id,
                    parameterName = c.parameterName,
                    controlType = c.controlType,
                    datasourceId = effectiveDatasourceId,
                    optionsQuery = c.optionsQuery,
                    sliderMin = c.sliderMin,
                    sliderMax = c.sliderMax,
                    sliderStep = c.sliderStep,
                    config = configMap,
                    sortOrder = c.sortOrder
                )
            )
        }

        // Set category if importing as template
        if (req.asTemplate && cfg.category != null) {
            val report = reportRepo.findById(created.id).orElse(null)
            report?.let {
                it.templateCategory = cfg.category
                reportRepo.save(it)
            }
        }

        // Create layers, annotations, tooltips and visibility rules of the widgets
        var totalLayers = 0
        cfg.widgets.forEach { wCfg ->
            val newWidgetId = sortOrderToWidgetId[wCfg.sortOrder] ?: return@forEach
            wCfg.annotations.forEach { a ->
                vizService.createAnnotation(AnnotationRequest(
                    widgetId = newWidgetId, annotationType = a.annotationType, axis = a.axis,
                    value = a.value, valueEnd = a.valueEnd, label = a.label, color = a.color,
                    lineStyle = a.lineStyle, lineWidth = a.lineWidth, opacity = a.opacity,
                    fillColor = a.fillColor, fillOpacity = a.fillOpacity, position = a.position,
                    fontSize = a.fontSize, isVisible = a.isVisible, sortOrder = a.sortOrder,
                    config = readMap(a.config)
                ))
            }
            wCfg.tooltip?.let { t ->
                vizService.saveTooltipConfig(TooltipConfigRequest(
                    widgetId = newWidgetId, isEnabled = t.isEnabled, showTitle = t.showTitle,
                    titleField = t.titleField, fields = t.fields, showSparkline = t.showSparkline,
                    sparklineField = t.sparklineField, htmlTemplate = t.htmlTemplate,
                    config = readMap(t.config)
                ))
            }
            wCfg.visibilityRules.forEach { r ->
                interactiveService.createVisibilityRule(VisibilityRuleRequest(
                    widgetId = newWidgetId, ruleType = r.ruleType, parameterName = r.parameterName,
                    operator = r.operator, expectedValue = r.expectedValue
                ))
            }
            wCfg.layers.forEach { lCfg ->
                val seriesConfigMap: Map<String, Any?> = try {
                    objectMapper.readValue(lCfg.seriesConfig)
                } catch (_: Exception) { emptyMap() }
                val paramMappingMap: Map<String, Any?> = try {
                    objectMapper.readValue(lCfg.paramMapping)
                } catch (_: Exception) { emptyMap() }
                interactiveService.createLayer(ChartLayerRequest(
                    widgetId = newWidgetId, name = lCfg.name, label = lCfg.label,
                    datasourceId = req.datasourceId,
                    rawSql = lCfg.rawSql,
                    chartType = lCfg.chartType, axis = lCfg.axis, color = lCfg.color,
                    opacity = lCfg.opacity, isVisible = lCfg.isVisible, sortOrder = lCfg.sortOrder,
                    seriesConfig = seriesConfigMap,
                    categoryField = lCfg.categoryField, valueField = lCfg.valueField,
                    paramMapping = paramMappingMap
                ))
                totalLayers++
            }
        }

        cfg.actions.forEach { a ->
            val sourceId = a.sourceWidgetSortOrder?.let { sortOrderToWidgetId[it] }
            if (a.sourceWidgetSortOrder != null && sourceId == null) {
                log.warn("Action '{}' skipped: no widget with sortOrder {}", a.name, a.sourceWidgetSortOrder)
                return@forEach
            }
            interactiveService.createAction(DashboardActionRequest(
                reportId = created.id, name = a.name, actionType = a.actionType, triggerType = a.triggerType,
                sourceWidgetId = sourceId,
                targetWidgetIds = a.targetWidgetSortOrders?.let { t ->
                    mapWidgetTargets(t) { sortOrderToWidgetId[it.toInt()] }
                },
                sourceField = a.sourceField, targetField = a.targetField, urlTemplate = a.urlTemplate,
                config = readMap(a.config)
            ))
        }

        cfg.overlays.forEach { o ->
            interactiveService.createOverlay(OverlayRequest(
                reportId = created.id, overlayType = o.overlayType, content = o.content,
                positionX = o.positionX, positionY = o.positionY, width = o.width, height = o.height,
                opacity = o.opacity, zIndex = o.zIndex, linkUrl = o.linkUrl, isVisible = o.isVisible,
                style = readMap(o.style)
            ))
        }

        log.info("Imported report '{}' (id={}, widgets={}, params={}, controls={}, layers={}, actions={}, overlays={})",
            reportName, created.id, created.widgets.size, created.parameters.size,
            cfg.parameterControls.size, totalLayers, cfg.actions.size, cfg.overlays.size)

        return ImportResult(
            reportId = created.id,
            name = reportName,
            widgetCount = created.widgets.size,
            parameterCount = created.parameters.size
        )
    }

    // ═══════════════════════════════════════════

    private fun readMap(json: String): Map<String, Any?> =
        try { objectMapper.readValue(json) } catch (_: Exception) { emptyMap() }

    // Target list of an action: widget ids separated by commas, "*" for all widgets, "-N" excludes widget N.
    // Targets that do not map are dropped.
    private fun mapWidgetTargets(targets: String, map: (Long) -> Long?): String =
        targets.split(",").map { it.trim() }.mapNotNull { t ->
            when {
                t == "*" -> t
                t.startsWith("-") -> t.drop(1).toLongOrNull()?.let(map)?.let { "-$it" }
                else -> t.toLongOrNull()?.let(map)?.toString()
            }
        }.joinToString(",")

    // Moves the widget list of a button from one chartConfig key to the other, mapping every element
    private fun remapToggledWidgets(chartConfig: String, from: String, to: String, map: (Long) -> Long?): String {
        val root = try { objectMapper.readTree(chartConfig) as? ObjectNode } catch (_: Exception) { null }
            ?: return chartConfig
        val ids = root.get(from)?.takeIf { it.isArray } ?: return chartConfig
        val mapped = objectMapper.createArrayNode()
        ids.forEach { node -> map(node.asLong())?.let { mapped.add(it) } }
        root.remove(from)
        root.set<ArrayNode>(to, mapped)
        return objectMapper.writeValueAsString(root)
    }

    private fun Report.toTemplateItem() = TemplateListItem(
        id = id, name = name, description = description,
        category = templateCategory, preview = templatePreview,
        thumbnailUrl = thumbnailUrl, widgetCount = widgets.size,
        createdAt = createdAt.toString()
    )

    companion object {
        // Button chartConfig keys: widget ids in the report, widget sortOrders in the export
        private const val TOGGLE_IDS = "toggleWidgetIds"
        private const val TOGGLE_SORT_ORDERS = "toggleWidgetSortOrders"
    }
}
