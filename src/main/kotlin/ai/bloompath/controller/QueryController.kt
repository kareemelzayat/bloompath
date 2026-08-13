package ai.bloompath.controller

import ai.bloompath.dto.QueryRequest
import ai.bloompath.dto.QueryResponse
import ai.bloompath.service.QaEngineService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule

@Controller("/api/v1")
@Secured(SecurityRule.IS_ANONYMOUS)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class QueryController(private val qaEngineService: QaEngineService) {

    @Post("/query")
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun query(@Body request: QueryRequest): QueryResponse =
        qaEngineService.answer(request)
}
