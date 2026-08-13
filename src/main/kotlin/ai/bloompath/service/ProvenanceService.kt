package ai.bloompath.service

import ai.bloompath.dto.ProvenanceRecord
import jakarta.inject.Singleton
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.*

interface ProvenanceService {
    fun calculate(
        connection: Connection,
        sql: String,
        parameters: List<Any?>,
        snapshot: QuerySnapshot
    ): List<ProvenanceRecord>
}

/** Calculates record lineage using JDBC metadata rather than schema constants. */
@Singleton
class JdbcProvenanceService(
    private val metadataProvider: DatabaseMetadataProvider
) : ProvenanceService {

    override fun calculate(
        connection: Connection,
        sql: String,
        parameters: List<Any?>,
        snapshot: QuerySnapshot
    ): List<ProvenanceRecord> = if (AggregateQuery.isAggregate(sql)) {
        aggregateProvenance(connection, sql, parameters)
    } else {
        rowProvenance(connection, snapshot)
    }

    private fun rowProvenance(
        connection: Connection,
        snapshot: QuerySnapshot
    ): List<ProvenanceRecord> {
        val keysByTable = snapshot.columns
            .mapNotNull { column -> column.sourceTable?.let { it to column } }
            .distinctBy { it.second.sourceTable to it.second.label.lowercase() }
            .groupBy({ it.first }, { it.second })
            .mapValues { (table, columns) ->
                val primaryKeys = metadataProvider.primaryKeys(connection, table)
                columns.filter { it.label.normalized() in primaryKeys }
            }

        return snapshot.rows.flatMap { row ->
            keysByTable.flatMap { (table, keyColumns) ->
                keyColumns.mapNotNull { keyColumn ->
                    val recordId = row.valueFor(keyColumn.label)?.toString() ?: return@mapNotNull null
                    val fields = snapshot.columns
                        .filter { it.sourceTable == table }
                        .map { it.label.normalized() }
                        .toSet()
                        .toList()
                    ProvenanceRecord(table, recordId, fields)
                }
            }
        }.distinctBy { it.table to it.recordId }
    }

    private fun aggregateProvenance(
        connection: Connection,
        sql: String,
        parameters: List<Any?>
    ): List<ProvenanceRecord> {
        val source = AggregateQuery.source(sql) ?: return emptyList()
        val primaryKeys = metadataProvider.primaryKeys(connection, source.table)
        if (primaryKeys.isEmpty()) return emptyList()

        val contributorSql = AggregateQuery.contributorSql(sql, source, primaryKeys)
        connection.prepareStatement(contributorSql).use { statement ->
            bindParameters(statement, parameters)
            statement.executeQuery().use { resultSet ->
                val records = mutableListOf<ProvenanceRecord>()
                while (resultSet.next()) {
                    primaryKeys.forEach { key ->
                        val value = resultSet.getObject(key) ?: return@forEach
                        records += ProvenanceRecord(
                            table = source.table,
                            recordId = value.toString(),
                            fieldsUsed = primaryKeys
                        )
                    }
                }
                return records.distinctBy { it.table to it.recordId }
            }
        }
    }

    private fun bindParameters(statement: PreparedStatement, parameters: List<Any?>) {
        parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
    }
}

interface DatabaseMetadataProvider {
    fun primaryKeys(connection: Connection, table: String): List<String>
}

@Singleton
class JdbcDatabaseMetadataProvider : DatabaseMetadataProvider {
    override fun primaryKeys(connection: Connection, table: String): List<String> {
        val candidates = listOf(table, table.uppercase(Locale.ROOT), table.lowercase(Locale.ROOT)).distinct()
        val keys = candidates.firstNotNullOfOrNull { candidate ->
            connection.metaData.getPrimaryKeys(null, null, candidate).use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.getShort("KEY_SEQ").toInt() to resultSet.getString("COLUMN_NAME").normalized())
                    }
                }.takeIf { it.isNotEmpty() }
            }
        } ?: return emptyList()
        return keys.sortedBy { it.first }.map { it.second }
    }
}

private object AggregateQuery {
    private val aggregatePattern = Regex("\\b(count|sum|avg|min|max)\\s*\\(", RegexOption.IGNORE_CASE)
    private val sourcePattern = Regex(
        "\\bfrom\\s+([\\\"\\w.]+)(?:\\s+(?:as\\s+)?([\\\"\\w]+))?",
        RegexOption.IGNORE_CASE
    )
    private val clausePattern = Regex("\\s+(group\\s+by|having|order\\s+by|limit|offset|fetch)\\b", RegexOption.IGNORE_CASE)

    fun isAggregate(sql: String): Boolean = aggregatePattern.containsMatchIn(sql)

    fun source(sql: String): Source? {
        val match = sourcePattern.find(sql) ?: return null
        val table = match.groupValues[1].unquote().substringAfterLast('.').lowercase(Locale.ROOT)
        val candidateAlias = match.groupValues[2].ifBlank { table }.unquote()
        val alias = if (candidateAlias.lowercase(Locale.ROOT) in SQL_KEYWORDS) table else candidateAlias
        return Source(table, alias)
    }

    fun contributorSql(sql: String, source: Source, primaryKeys: List<String>): String {
        val fromIndex = Regex("\\bfrom\\b", RegexOption.IGNORE_CASE).find(sql)!!.range.first
        val fromClause = sql.substring(fromIndex).trim().trimEnd(';').let { clause ->
            clausePattern.find(clause)?.let { clause.substring(0, it.range.first) } ?: clause
        }
        val projection = primaryKeys.joinToString(", ") { "${source.alias}.$it" }
        return "SELECT $projection $fromClause"
    }

    data class Source(val table: String, val alias: String)

    private val SQL_KEYWORDS = setOf(
        "where", "join", "left", "right", "inner", "outer", "cross", "group",
        "having", "order", "limit", "offset", "fetch", "union"
    )
}

private fun String.normalized(): String =
    lowercase(Locale.ROOT).substringAfterLast('.').removeSurrounding("\"")

private fun String.unquote(): String = removeSurrounding("\"")

private fun Map<String, Any?>.valueFor(label: String): Any? =
    entries.firstOrNull { it.key.normalized() == label.normalized() }?.value
