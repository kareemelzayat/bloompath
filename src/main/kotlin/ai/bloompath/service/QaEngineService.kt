package ai.bloompath.service

import ai.bloompath.ai.AnswerSynthesisService
import ai.bloompath.ai.IntentAction
import ai.bloompath.ai.IntentParserService
import ai.bloompath.dto.QueryRequest
import ai.bloompath.dto.QueryResponse
import ai.bloompath.dto.QueryStatus
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

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
                val context = buildRetrievedContext(question, sql, result)
                LOG.info("Generated query: ${decision.sql}")
                val answer = answerSynthesisService.synthesize(context).answer
                QueryResponse(
                    status = QueryStatus.SUCCESS,
                    question = question,
                    answer = answer,
                    provenance = result.provenance
                )
            }
        }
    }

    private fun buildRetrievedContext(question: String, sql: String, queryResult: DatabaseQueryResult): String =
        """
        QUESTION:
        $question

        SQL QUERY:
        $sql
        
        RETRIEVED DATABASE ROWS:
        ${queryResult.rows.joinToString(separator = "\n")}
        """.trimIndent()

    private companion object {
        const val REFUSAL_ANSWER =
            "I cannot answer this question. Financial and budget data are not tracked in BloomPath operational records."

        val LOG = LoggerFactory.getLogger(QaEngineService::class.java)
    }
}
