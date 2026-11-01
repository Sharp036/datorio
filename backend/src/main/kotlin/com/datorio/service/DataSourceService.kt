package com.datorio.service

import com.datorio.datasource.ConnectionManager
import com.datorio.datasource.TableInfo
import com.datorio.model.DataSource
import com.datorio.model.dto.*
import com.datorio.query.ReadOnlySql
import com.datorio.repository.DataSourceRepository
import com.datorio.repository.ImportSourceRepository
import com.datorio.repository.SavedQueryRepository
import com.datorio.repository.UserRepository
import com.datorio.security.SecretCipher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DataSourceService(
    private val dataSourceRepository: DataSourceRepository,
    private val userRepository: UserRepository,
    private val connectionManager: ConnectionManager,
    private val secretCipher: SecretCipher,
    private val savedQueryRepository: SavedQueryRepository,
    private val importSourceRepository: ImportSourceRepository
) {
    fun findAll(): List<DataSourceResponse> {
        return dataSourceRepository.findByIsActiveTrue().map { it.toResponse() }
    }

    fun findById(id: Long): DataSourceResponse {
        return getEntity(id).toResponse()
    }

    @Transactional
    fun create(request: DataSourceCreateRequest, username: String): DataSourceResponse {
        val user = userRepository.findByUsername(username).orElse(null)
        val ds = DataSource(
            name = request.name,
            description = request.description,
            type = request.type,
            host = request.host,
            port = request.port,
            databaseName = request.databaseName,
            username = request.username,
            passwordEnc = secretCipher.encrypt(request.password),
            extraParams = request.extraParams,
            createdBy = user
        )
        return dataSourceRepository.save(ds).toResponse()
    }

    @Transactional
    fun update(id: Long, request: DataSourceCreateRequest): DataSourceResponse {
        val ds = getEntity(id)
        ds.name = request.name
        ds.description = request.description
        ds.host = request.host
        ds.port = request.port
        ds.databaseName = request.databaseName
        ds.username = request.username
        if (request.password != null) {
            ds.passwordEnc = secretCipher.encrypt(request.password)
        } else if (!secretCipher.isEncrypted(ds.passwordEnc)) {
            ds.passwordEnc = secretCipher.encrypt(ds.passwordEnc)
        }
        ds.extraParams = request.extraParams
        ds.updatedAt = java.time.OffsetDateTime.now()

        connectionManager.evictPool(id)  // force pool recreation
        return dataSourceRepository.save(ds).toResponse()
    }

    @Transactional
    fun delete(id: Long) {
        getEntity(id)
        // Saved queries, import sources and data models point at the data source; a foreign key error
        // would be a 500 with no explanation, so name what still uses it
        val queries = savedQueryRepository.findByDatasourceId(id).size
        val sources = importSourceRepository.findAll().count { it.datasource.id == id }
        require(queries == 0 && sources == 0) {
            "The data source is still used by $queries saved query(ies) and $sources import source(s); delete or move them first"
        }
        connectionManager.evictPool(id)
        dataSourceRepository.deleteById(id)
    }

    fun testConnection(id: Long): ConnectionTestResult {
        val ds = getEntity(id)
        return connectionManager.testConnection(ds)
    }

    fun executeQuery(request: QueryExecuteRequest): QueryResult {
        val ds = getEntity(request.datasourceId)
        ReadOnlySql.requireReadOnly(request.sql)
        return connectionManager.executeQuery(ds, request.sql, request.limit)
    }

    fun getSchema(id: Long): List<TableInfo> {
        val ds = getEntity(id)
        return connectionManager.getSchemaInfo(ds)
    }

    private fun getEntity(id: Long): DataSource {
        return dataSourceRepository.findById(id)
            .orElseThrow { NoSuchElementException("DataSource not found: $id") }
    }

    private fun DataSource.toResponse() = DataSourceResponse(
        id = id,
        name = name,
        description = description,
        type = type,
        host = host,
        port = port,
        databaseName = databaseName,
        username = username,
        isActive = isActive,
        createdAt = createdAt.toString()
    )
}
