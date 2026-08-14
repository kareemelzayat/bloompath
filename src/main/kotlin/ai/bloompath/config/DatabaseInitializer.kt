package ai.bloompath.config

import io.micronaut.context.annotation.Context
import io.micronaut.context.event.StartupEvent
import io.micronaut.data.connection.annotation.Connectable
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Singleton
import java.sql.Connection
import javax.sql.DataSource

/** Creates and seeds the PoC database once the JDBC datasource is available. */
@Context
@Singleton
open class DatabaseInitializer(private val dataSource: DataSource) {

    @Connectable
    @EventListener
    open fun initialize(@Suppress("UNUSED_PARAMETER") event: StartupEvent) {
        val script = DatabaseInitializer::class.java.getResource("/schema.sql")
            ?.readText()
            ?: error("Database schema resource /schema.sql was not found")

        val connection = dataSource.connection
        executeScript(connection, script)
        if (!isInitialized(connection)) error("Database schema was not initialized")
    }

    private fun isInitialized(connection: Connection): Boolean =
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT 1
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'CLIENTS'
                """.trimIndent()
            ).use { resultSet -> resultSet.next() }
        }

    private fun executeScript(connection: Connection, script: String) {
        val statements = script
            .lineSequence()
            .joinToString("\n") { it.substringBefore("--") }
            .split(';')
            .map(String::trim)
            .filter(String::isNotEmpty)

        connection.createStatement().use { statement ->
            statements.forEach(statement::execute)
        }
    }
}
