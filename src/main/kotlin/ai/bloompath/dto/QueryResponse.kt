package ai.bloompath.dto

import io.micronaut.serde.annotation.Serdeable

enum class QueryStatus {
    SUCCESS, AMBIGUOUS, REFUSED
}

@Serdeable
data class ProvenanceRecord(
    val table: String,
    val recordId: String,
    val fieldsUsed: List<String>
)

@Serdeable
data class QueryResponse(
    val status: QueryStatus,
    val question: String,
    val answer: String,
    val provenance: List<ProvenanceRecord> = emptyList(),
    val clarificationPrompt: String? = null
)
