package ai.bloompath

import ai.bloompath.dto.QueryRequest
import ai.bloompath.dto.QueryResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class QueryControllerTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Test
    fun testMetricQuery() {
        val response = post("How many active cases are assigned to John Doe in Youth Outreach?")

        assertEquals(HttpStatus.OK, response.status)
        assertEquals("SUCCESS", response.body()!!.status.name)
        assertTrue(response.body()!!.answer.isNotEmpty())
        assertTrue(response.body()!!.provenance.isNotEmpty())
    }

    @Test
    fun testTemporalQuery() {
        val response = post("What was the last service activity recorded for Client CLI-105?")
        val body = response.body()!!

        assertEquals(HttpStatus.OK, response.status)
        assertEquals("SUCCESS", body.status.name)
        assertTrue(body.answer.contains("Intake", ignoreCase = true))
    }

    @Test
    fun testCrossEntityQuery() {
        val response = post("List all clients in Housing Support who have flagged notes?")
        val body = response.body()!!

        assertEquals(HttpStatus.OK, response.status)
        assertEquals("SUCCESS", body.status.name)
        assertTrue(body.provenance.any { it.recordId == "CLI-103" })
        assertTrue(body.provenance.any { it.recordId == "CLI-102" })
    }

    @Test
    fun testCompoundQuery() {
        val response = post("Can we know how many open cases we have for Aisha Patel, with Id: CLI-103? And how many programs is she enrolled to?")
        val body = response.body()!!

        assertEquals(HttpStatus.OK, response.status)
        assertEquals("SUCCESS", body.status.name)
        assertTrue(body.answer.contains("1", ignoreCase = true))
        assertTrue(body.provenance.any { it.table == "case_statuses" })
    }

    @Test
    fun testAmbiguousQuery() {
        val response = post("What was John's last activity?")
        val body = response.body()!!

        assertEquals(HttpStatus.OK, response.status)
        assertEquals("AMBIGUOUS", body.status.name)
        assertNotNull(body.clarificationPrompt)
    }

    @Test
    fun testRefusalQuery() {
        val response = post("What is the annual operating budget for Housing Support?")
        val body = response.body()!!

        assertEquals(HttpStatus.OK, response.status)
        assertEquals("REFUSED", body.status.name)
        assertFalse(body.provenance.isNotEmpty())
    }

    private fun post(question: String) = client.toBlocking().exchange(
        HttpRequest.POST("/api/v1/query", QueryRequest(question)),
        QueryResponse::class.java
    )
}
