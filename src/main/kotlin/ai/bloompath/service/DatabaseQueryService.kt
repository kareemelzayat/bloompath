package ai.bloompath.service

import ai.bloompath.dto.ProvenanceRecord
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Executes read-only SQL. Provenance calculation is delegated so this class
 * does not need to know anything about application tables or key names.
 */
@Singleton
@Transactional
class DatabaseQueryService(
    private val dataSource: DataSource,
    private val provenanceService: ProvenanceService
) {

    fun execute(sql: String, parameters: List<Any?> = emptyList()): DatabaseQueryResult {
        require(sql.isNotBlank()) { "SQL query must not be blank" }
        LOG.info("Executing query: {}", sql)
        LOG.info("Parameters: {}", parameters.joinToString())

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                bindParameters(statement, parameters)
                statement.executeQuery().use { resultSet ->
                    val snapshot = QuerySnapshot.from(resultSet)
                    val provenance = provenanceService.calculate(
                        connection = connection,
                        sql = sql,
                        parameters = parameters,
                        snapshot = snapshot
                    )
                    return DatabaseQueryResult(
                        rows = snapshot.rows,
                        provenance = provenance
                    )
                }
            }
        }
    }

    private fun bindParameters(statement: PreparedStatement, parameters: List<Any?>) {
        parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
    }

    companion object {
        val LOG = LoggerFactory.getLogger(DatabaseQueryService::class.java)
    }
}

/** The rows and provenance produced by a parameterized database query. */
data class DatabaseQueryResult(
    val rows: List<Map<String, Any?>>,
    val provenance: List<ProvenanceRecord>
)

data class QuerySnapshot(
    val rows: List<Map<String, Any?>>,
    val columns: List<ResultColumn>
) {
    companion object {
        fun from(resultSet: ResultSet): QuerySnapshot {
            val metadata = resultSet.metaData
            val columns = (1..metadata.columnCount).map { index ->
                ResultColumn(
                    index = index,
                    label = metadata.getColumnLabel(index).ifBlank { metadata.getColumnName(index) },
                    sourceTable = metadata.getTableName(index).trim().lowercase().ifBlank { null }
                )
            }
            val rows = buildList {
                while (resultSet.next()) {
                    add(columns.associate { column ->
                        column.label to resultSet.getObject(column.index)
                    })
                }
            }
            return QuerySnapshot(rows, columns)
        }
    }
}

data class ResultColumn(
    val index: Int,
    val label: String,
    val sourceTable: String?
)
