package ai.bloompath.service

import io.micronaut.data.connection.annotation.Connectable
import io.micronaut.context.annotation.Requires
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.sql.DataSource

@MicronautTest(transactional = false)
open class DatabaseQueryServiceTest {

    @field:Inject
    lateinit var databaseQueryService: DatabaseQueryService

    @field:Inject
    lateinit var databaseFixture: DatabaseQueryServiceTestFixture

    @BeforeEach
    fun setUp() = databaseFixture.createTable()

    @AfterEach
    fun tearDown() = databaseFixture.dropTable()

    @Test
    fun `normal query returns provenance for dynamically discovered primary key`() {
        val result = databaseQueryService.execute(
            "SELECT record_key, description FROM provenance_test_records"
        )

        assertEquals(1, result.rows.size)
        assertEquals(
            setOf("provenance_test_records:REC-001"),
            result.provenance.map { "${it.table}:${it.recordId}" }.toSet()
        )
        assertTrue(result.provenance.single().fieldsUsed.contains("record_key"))
        assertTrue(result.provenance.single().fieldsUsed.contains("description"))
    }

    @Test
    fun `aggregate query returns provenance for every contributing case status`() {
        val result = databaseQueryService.execute(
            """
            SELECT COUNT(*) AS active_count
            FROM case_statuses cs
            JOIN staff s ON s.staff_id = cs.assigned_staff_id
            JOIN programs p ON p.program_id = cs.program_id
            WHERE cs.status = ? AND s.full_name = ? AND p.name = ?
            """.trimIndent(),
            listOf("Active", "Sarah Jenkins", "Youth Outreach")
        )

        assertEquals(1, result.rows.size)
        assertEquals(2L, (rowValue(result.rows.single(), "active_count") as Number).toLong())
        assertEquals(
            setOf("CAS-001", "CAS-002"),
            result.provenance
                .filter { it.table == "case_statuses" }
                .map { it.recordId }
                .toSet()
        )
    }

    @Test
    fun `aggregate query with no matching rows has no provenance`() {
        val result = databaseQueryService.execute(
            """
            SELECT COUNT(*) AS active_count
            FROM case_statuses
            WHERE status = ? AND client_id = ?
            """.trimIndent(),
            listOf("Active", "CLI-999")
        )

        assertEquals(1, result.rows.size)
        assertEquals(0L, (rowValue(result.rows.single(), "active_count") as Number).toLong())
        assertTrue(result.provenance.isEmpty())
    }

    @Test
    fun `temporal query preserves activity provenance`() {
        val result = databaseQueryService.execute(
            """
            SELECT activity_id, activity_type, activity_date, notes
            FROM service_activities
            WHERE client_id = ?
            ORDER BY activity_date DESC
            LIMIT 1
            """.trimIndent(),
            listOf("CLI-101")
        )

        assertEquals("Follow-up", rowValue(result.rows.single(), "activity_type"))
        assertEquals(
            setOf("ACT-002"),
            result.provenance
                .filter { it.table == "service_activities" }
                .map { it.recordId }
                .toSet()
        )
    }

    private fun rowValue(row: Map<String, Any?>, column: String): Any? =
        row.entries.first { it.key.equals(column, ignoreCase = true) }.value
}

@Singleton
@Requires(env = ["test"])
open class DatabaseQueryServiceTestFixture(private val dataSource: DataSource) {

    @Connectable
    open fun createTable() {
        dataSource.connection.use { connection ->
            ensureSeedSchema(connection)
            connection.createStatement().use { statement ->
                statement.execute("DROP TABLE IF EXISTS provenance_test_records")
                statement.execute(
                    """
                    CREATE TABLE provenance_test_records (
                        record_key VARCHAR(36) PRIMARY KEY,
                        description VARCHAR(255) NOT NULL
                    )
                    """.trimIndent()
                )
                statement.execute(
                    "INSERT INTO provenance_test_records (record_key, description) VALUES ('REC-001', 'first record')"
                )
            }
        }
    }

    private fun ensureSeedSchema(connection: java.sql.Connection) {
        val hasClients = connection.metaData.getTables(null, null, "CLIENTS", arrayOf("TABLE")).use { it.next() }
        if (hasClients) return

        val script = javaClass.getResource("/schema.sql")!!.readText()
            .lineSequence()
            .joinToString("\n") { it.substringBefore("--") }
            .split(';')
            .map(String::trim)
            .filter(String::isNotEmpty)

        connection.createStatement().use { statement ->
            script.forEach(statement::execute)
        }
    }

    @Connectable
    open fun dropTable() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP TABLE IF EXISTS provenance_test_records")
            }
        }
    }
}
