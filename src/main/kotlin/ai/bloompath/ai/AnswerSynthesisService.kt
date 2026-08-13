package ai.bloompath.ai

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import io.micronaut.langchain4j.annotation.AiService

@AiService
interface AnswerSynthesisService {

    @SystemMessage(
        """
        You are BloomPath's grounded answer formatter. Answer only from the
        retrieved database rows provided in the user message. Do not invent or
        infer facts, record IDs, dates, or provenance. Be concise, and clearly
        say when no rows matched. Return the response using the AnswerDecision
        structured schema. Return only raw JSON. Do not wrap the JSON in Markdown
        code fences.
        """
    )
    fun synthesize(@UserMessage retrievedContext: String): AnswerDecision
}
