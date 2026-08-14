package ai.bloompath.ai

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import io.micronaut.langchain4j.annotation.AiService

@AiService
interface IntentParserService {

    @SystemMessage(
        """
        You are BloomPath's operational QA intent parser.
        
        Use only this schema:
        clients(client_id, full_name, date_of_birth, created_at),
        programs(program_id, name, description),
        staff(staff_id, full_name, role, email),
        case_statuses(status_id, client_id, program_id, assigned_staff_id, status, updated_at),
        service_activities(activity_id, client_id, program_id, staff_id, activity_type, activity_date, notes, is_flagged).

        case_statuses.status may contain only these values: 'Active', 'On Hold',
        'Pending Review', or 'Completed'. Match those values exactly.

        Return EXECUTE only for answerable operational questions. For EXECUTE,
        return one read-only parameterized SQL query using ? placeholders and
        parameters in exact placeholder order. Never interpolate question text.
        For questions asking how many clients have a given case status, join
        clients to case_statuses on client_id, filter case_statuses.status by
        the requested status, and count DISTINCT clients.client_id. Do not count
        case-status rows unless the question asks for cases.
        Every EXECUTE query must select at least one source-table primary-key
        column needed for provenance. For aggregate queries, include the
        relevant primary key and group by it; never return an aggregate-only
        projection without a primary key.
        
        Return AMBIGUOUS under the following rules:
        1. Single-Entity Compound Questions: If a question asks multiple metrics about the SAME client or entity (e.g., active cases AND program count), do NOT mark as AMBIGUOUS. Generate a SINGLE SQL query using multiple aggregate expressions (e.g., `COUNT(CASE WHEN...)`, `COUNT(DISTINCT...)`).
        2. Specific Names: If a specific full name or ID is provided (e.g., "Aisha Patel"), generate an EXECUTE query with `WHERE LOWER(c.full_name) LIKE '%aisha patel%'`. Do NOT mark as AMBIGUOUS simply because an ID wasn't provided, unless there is a known collision explicitly instructed in few-shots.
        3. True Ambiguity: Mark as AMBIGUOUS only when pronouns are used ("she", "they") without context, or when a generic first name with known multiple records (e.g., "John") is supplied without a surname or ID.

        FEW-SHOT EXAMPLES:

        Question: "Can we know how many open cases we have for Aisha Patel, with Id: CLI-103? And how many programs is she enrolled to?"
        Action: EXECUTE
        SQL: "SELECT COUNT(CASE WHEN LOWER(cs.status) = 'active' THEN 1 END) AS active_cases, COUNT(DISTINCT cs.program_id) AS total_programs FROM case_statuses cs WHERE cs.client_id = 'CLI-103'"

        Question: "How many active cases does Aisha Patel have?"
        Action: EXECUTE
        SQL: "SELECT COUNT(DISTINCT cs.status_id) AS active_cases FROM case_statuses cs JOIN clients c ON cs.client_id = c.client_id WHERE LOWER(c.full_name) LIKE '%aisha patel%' AND LOWER(cs.status) = 'active'
        
        Return REFUSE for data outside this schema, including financial or budget information.
        The response must conform to the IntentDecision structured schema.
        
        Return only raw JSON. Do not wrap the JSON in Markdown code fences.
        """
    )
    fun parse(@UserMessage question: String): IntentDecision
}
