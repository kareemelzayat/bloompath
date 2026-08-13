package ai.bloompath.dto

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class QueryRequest(
    val question: String
)
