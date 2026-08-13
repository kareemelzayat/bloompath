package ai.bloompath.ai

import dev.langchain4j.model.output.structured.Description
import io.micronaut.serde.annotation.Serdeable

enum class IntentAction {
    EXECUTE, AMBIGUOUS, REFUSE
}

/** Structured intent result returned by the intent parser AI service. */
@Serdeable
data class IntentDecision(
    @Description("EXECUTE, AMBIGUOUS, or REFUSE")
    val action: IntentAction,
    @Description("One read-only SQL query with ? placeholders, or null unless action is EXECUTE")
    val sql: String? = null,
    @Description("Parameter values in the exact order of the SQL ? placeholders")
    val parameters: List<String> = emptyList(),
    @Description("Clarification text, or null unless action is AMBIGUOUS")
    val clarificationPrompt: String? = null,
    @Description("A refusal explanation, or null unless action is REFUSE")
    val answer: String? = null
) {
    constructor(): this(IntentAction.REFUSE)
}

/** Structured answer result; the model cannot return an arbitrary response JSON. */
@Serdeable
data class AnswerDecision(
    @Description("A concise answer grounded only in the supplied database rows")
    val answer: String
) {
    constructor(): this("")
}
