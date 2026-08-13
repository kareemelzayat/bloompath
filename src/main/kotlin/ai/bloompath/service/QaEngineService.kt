package ai.bloompath.service

import ai.bloompath.ai.AnswerSynthesisService
import ai.bloompath.ai.IntentAction
import ai.bloompath.ai.IntentParserService
import ai.bloompath.dto.QueryRequest
import ai.bloompath.dto.QueryResponse
import ai.bloompath.dto.QueryStatus
import jakarta.inject.Singleton

/** Coordinates intent parsing, grounded execution, provenance, and phrasing. */
@Singleton
class QaEngineService(
    private val intentParserService: IntentParserService,
    private val answerSynthesisService: AnswerSynthesisService,
    private val databaseQueryService: DatabaseQueryService
) {

    fun answer(request: QueryRequest): QueryResponse = answer(request.question)

    fun answer(question: String): QueryResponse {
        val decision = intentParserService.parse(question)

        return when (decision.action) {
            IntentAction.AMBIGUOUS -> QueryResponse(
                status = QueryStatus.AMBIGUOUS,
                question = question,
                answer = "",
                clarificationPrompt = decision.clarificationPrompt
            )

            IntentAction.REFUSE -> QueryResponse(
                status = QueryStatus.REFUSED,
                question = question,
                answer = decision.answer ?: REFUSAL_ANSWER
            )

            IntentAction.EXECUTE -> {
                val sql = decision.sql ?: error("EXECUTE intent did not include SQL")
                val result = databaseQueryService.execute(sql, decision.parameters)
                val context = buildRetrievedContext(question, result.rows)
                QueryResponse(
                    status = QueryStatus.SUCCESS,
                    question = question,
                    answer = answerSynthesisService.synthesize(context).answer,
                    provenance = result.provenance
                )
            }
        }
    }

    private fun buildRetrievedContext(question: String, rows: List<Map<String, Any?>>): String =
        """
        QUESTION:
        $question

        RETRIEVED DATABASE ROWS:
        ${rows.joinToString(separator = "\n")}
        """.trimIndent()

    private companion object {
        const val REFUSAL_ANSWER =
            "I cannot answer this question. Financial and budget data are not tracked in BloomPath operational records."
    }
}
