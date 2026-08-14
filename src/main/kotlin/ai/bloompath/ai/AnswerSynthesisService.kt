package ai.bloompath.ai

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import io.micronaut.langchain4j.annotation.AiService

@AiService
interface AnswerSynthesisService {

    @SystemMessage(
        """
            You are a grounded assistant for BloomPath. Synthesize a concise answer using ONLY the provided query results.
            
            CONTEXT PROVIDED TO YOU:
                - User Question: {userQuestion}
                - Executed SQL Query: {executedSql}
                - Raw Result Rows: {rawRows}

            RULES:
            1. Use the SQL query to understand what the numbers/rows represent (e.g., if SQL filtered by status='active', refer to them as 'active' cases).
            2. Do not invent details not present in the results or infer facts.
            3. Be concise. If no database rows were provided, clearly say when no rows matched. 
            4. Return the response using the AnswerDecision structured schema.
            5. Return only raw JSON. Do not wrap the JSON in Markdown code fences.
        """
    )
    fun synthesize(@UserMessage retrievedContext: String): AnswerDecision
}
