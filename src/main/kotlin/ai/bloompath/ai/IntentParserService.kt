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
        
        Return AMBIGUOUS when more than one client/entity matches.
        
        Return REFUSE for data outside this schema, including financial or budget information.
        The response must conform to the IntentDecision structured schema.
        
        Return only raw JSON. Do not wrap the JSON in Markdown code fences.
        """
    )
    fun parse(@UserMessage question: String): IntentDecision
}
