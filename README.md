# 1. Title & Executive Overview

## Grounded Operational QA Service (BloomPath PoC)

BloomPath is a grounded operational question-answering service for frontline workers. Its core architectural choice is **deterministic Text-to-SQL plus code-derived provenance**, rather than generic vector or RAG search. The language model interprets an operational question and produces a read-only, parameterized SQL intent; Kotlin executes that query and derives record lineage directly from JDBC metadata and the returned rows. This guarantees zero hallucinated citations and exact, record-level traceability for every answer that includes provenance.

### Technology stack

- **Language and runtime:** Kotlin 2.4.10 on JVM 25
- **Framework:** Micronaut 5.1.0
- **Build tool:** Gradle Wrapper 9.7.0
- **Database:** H2 in-memory database (`jdbc:h2:mem:bloompath`) via Micronaut Data JDBC
- **LLM integration:** LangChain4j declarative services (`@AiService`) connected to local Ollama (`gemma4:e4b-mlx`)
- **Testing:** JUnit 5 and Micronaut Test

# 2. Architecture & Execution Pipeline

The service separates probabilistic language understanding from deterministic data access and lineage extraction:

```text
[Client POST /api/v1/query]
              |
              v
[Step 1: Intent & SQL Parser]
       [LangChain4j / Ollama]
              |
              v
[Step 2: Parameterized SQL Execution]
             [H2 via JDBC]
              |
              v
[Step 3: Programmatic Provenance Extraction]
       [100% Kotlin code-derived PK mapping]
              |
              v
[Step 4: Grounded Answer Synthesis]
              |
              v
       [Structured JSON Response]
```

The parser returns one of three explicit decisions. `QaEngineService` then short-circuits or continues the pipeline accordingly.

- **EXECUTE:** A valid operational question produces a read-only, parameterized SQL query. The query executes against H2, exact primary-key provenance is extracted from the JDBC `ResultSet` and database metadata, and a second declarative AI service synthesizes natural language using only the retrieved rows.
- **AMBIGUOUS:** Entity collision detection identifies multiple matching clients or entities—for example, more than one client named “John.” SQL execution is short-circuited and the response returns `status: "AMBIGUOUS"` with a `clarificationPrompt`.
- **REFUSE:** A data-boundary guardrail rejects an out-of-scope request, such as a financial-budget query. The response returns `status: "REFUSED"`, an explanation in `answer`, and an empty provenance array.

The API endpoint is `POST /api/v1/query` and accepts a JSON body containing a `question` string.

# 3. Setup & Quickstart Guide

## Prerequisites

- JDK 25
- Gradle 9.7.0 (the repository wrapper is recommended)
- Ollama installed and running locally

## Configuration

The service reads the following environment variables. Both have local-development defaults:

| Variable | Default | Purpose |
| --- | --- | --- |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server endpoint |
| `OLLAMA_MODEL_NAME` | `gemma4:e4b-mlx` | Chat model used for parsing and synthesis |

## Run locally

```bash
ollama pull gemma4:e4b-mlx
./gradlew run
```

Run the integration and unit test suite with:

```bash
./gradlew test
```

## API examples

### 1. Success: metric or aggregation query

```bash
curl -X POST http://localhost:8080/api/v1/query \
  -H 'Content-Type: application/json' \
  -d '{"question":"How many clients have an active case?"}'
```

Representative response shape:

```json
{
  "status": "SUCCESS",
  "question": "How many clients have an active case?",
  "answer": "3 clients have an active case.",
  "provenance": [
    {"table":"clients","recordId":"CLI-101","fieldsUsed":["client_id"]},
    {"table":"clients","recordId":"CLI-102","fieldsUsed":["client_id"]},
    {"table":"clients","recordId":"CLI-104","fieldsUsed":["client_id"]}
  ],
  "clarificationPrompt": null
}
```

The exact natural-language answer and returned contributing records depend on the generated query and current data snapshot.

### 2. Success: temporal / history query (Question Type 2)

```bash
curl -X POST http://localhost:8080/api/v1/query \
  -H 'Content-Type: application/json' \
  -d '{"question":"What was the last service activity recorded for Client CLI-101?"}'
```

Representative response shape:

```json
{
  "status": "SUCCESS",
  "question": "What was the last service activity recorded for Client CLI-101?",
  "answer": "The last service activity was a Follow-up on 2026-01-20 at 15:30. Follow-up completed and the next appointment was scheduled.",
  "provenance": [
    {
      "table": "service_activities",
      "recordId": "ACT-002",
      "fieldsUsed": ["activity_id", "client_id", "activity_type", "activity_date", "notes"]
    }
  ],
  "clarificationPrompt": null
}
```

This record-level example returns the activity type, date, notes, and provenance for the exact `service_activities.activity_id` rather than provenance for rows contributing to an aggregate.

### 3. Success: cross-entity filter query (Question Type 3)

```bash
curl -X POST http://localhost:8080/api/v1/query \
  -H 'Content-Type: application/json' \
  -d '{"question":"List all clients in Housing Support who have flagged or uncompleted notes."}'
```

Representative response shape:

```json
{
  "status": "SUCCESS",
  "question": "List all clients in Housing Support who have flagged or uncompleted notes.",
  "answer": "Maria Garcia and Aisha Patel have flagged or uncompleted notes in Housing Support.",
  "provenance": [
    {"table":"clients","recordId":"CLI-102","fieldsUsed":["client_id","full_name"]},
    {"table":"case_statuses","recordId":"CAS-006","fieldsUsed":["status_id","client_id","program_id","status"]},
    {"table":"service_activities","recordId":"ACT-003","fieldsUsed":["activity_id","client_id","program_id","activity_type","activity_date","notes","is_flagged"]},
    {"table":"clients","recordId":"CLI-103","fieldsUsed":["client_id","full_name"]},
    {"table":"case_statuses","recordId":"CAS-007","fieldsUsed":["status_id","client_id","program_id","status"]},
    {"table":"service_activities","recordId":"ACT-004","fieldsUsed":["activity_id","client_id","program_id","activity_type","activity_date","notes","is_flagged"]}
  ],
  "clarificationPrompt": null
}
```

This query demonstrates the multi-table join across `clients`, `case_statuses`, and `service_activities`, filtering for the `Housing Support` program and flagged service activity notes (`is_flagged = true`).

### 4. Ambiguous: entity collision and clarification

```bash
curl -X POST http://localhost:8080/api/v1/query \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the latest activity for John?"}'
```

Representative response shape:

```json
{
  "status": "AMBIGUOUS",
  "question": "What is the latest activity for John?",
  "answer": "",
  "provenance": [],
  "clarificationPrompt": "Which John do you mean? Please provide a client identifier or more identifying information."
}
```

### 5. Refusal: out-of-scope budget query

```bash
curl -X POST http://localhost:8080/api/v1/query \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the budget for the Housing Support program?"}'
```

Representative response shape:

```json
{
  "status": "REFUSED",
  "question": "What is the budget for the Housing Support program?",
  "answer": "I cannot answer this question. Financial and budget data are not tracked in BloomPath operational records.",
  "provenance": [],
  "clarificationPrompt": null
}
```

# 4. Database Schema & Domain Assumptions

## ERD breakdown

The PoC contains five core tables:

- **`clients`** — one record per client, identified by `client_id`, with name, date of birth, and creation timestamp.
- **`programs`** — operational programs identified by `program_id`, with a name and description.
- **`staff`** — staff members identified by `staff_id`, including name, role, and email.
- **`case_statuses`** — a client’s program-specific case state, identified by `status_id`; references `clients`, `programs`, and optionally `staff` through `assigned_staff_id`.
- **`service_activities`** — dated operational activities, identified by `activity_id`; references a client, program, and staff member and stores activity type, notes, and a flag state.

Relationships:

```text
  ┌──────────────────────────┐               ┌──────────────────────────┐
  │         clients          │               │         programs         │
  ├──────────────────────────┤               ├──────────────────────────┤
  │ PK  client_id            │               │ PK  program_id           │
  │     full_name            │               │     name                 │
  │     date_of_birth        │               │     description          │
  │     created_at           │               └────────────┬─────────────┘
  └─────────┬────────────────┘                            │
            │                                             │
            │ 1      ┌────────────────────────────────────┤ 1
            │        │                                    │
            ├────────│──────────────┐                     │
            │        │              │                     │
            │ N      │ N            │ N                   │ N
  ┌─────────┴────────┴───────┐    ┌─┴─────────────────────┴──────────┐
  │    service_activities    │    │          case_statuses           │
  ├──────────────────────────┤    ├──────────────────────────────────┤
  │ PK  activity_id          │    │ PK  status_id                    │
  │ FK  client_id            │    │ FK  client_id                    │
  │ FK  program_id           │    │ FK  program_id                   │
  │ FK  staff_id             │    │ FK  assigned_staff_id (nullable) │
  │     activity_type        │    │     status                       │
  │     activity_date        │    │     updated_at                   │
  │     notes                │    └──────────────────┬───────────────┘
  │     is_flagged           │                       │
  └─────────────┬────────────┘                       │
                │                                    │
                │ N                                  │ N
                │          ┌──────────────────┐      │
                └──────────┤      staff       ├──────┘
                         1 ├──────────────────┤ 0..1
                           │ PK  staff_id     │
                           │     full_name    │
                           │     role         │
                           │     email        │
                           └──────────────────┘

```

## Key Assumptions

- **X Case Definition:** A case is classified as **X** when its `case_statuses.status` value is **X**, where **X** may be `Active`, `Pending Review`, `On Hold`, or `Completed`.
- **Notes and service dates:** Notes lie under `service_activities`, and service activities are delivered on a specific date recorded in `service_activities.activity_date`.
- **Data boundaries:** Financial, budget, or medical diagnosis attributes are out of scope and trigger immediate query refusal.
- **Ambiguity rules:** Name collisions—for example, multiple clients matching “John”—short-circuit SQL execution and request clarification from the user.
- **Unassigned staff:** `case_statuses.assigned_staff_id` handles `NULL` values for unassigned program enrollments.

# 5. AI Usage Log (Evaluator Requirement)

## Agentic workflow overview

The codebase was fully generated through an iterative, phase-based agentic coding workflow using Codex powered by GPT-5.6-Luna, guided directly by the technical specification in [`SPECIFICATION.md`](SPECIFICATION.md).

Each project phase was prompted by a user request defining the next architectural or implementation objective. After the agent generated the code, a human read and reviewed the changes, provided feedback, and prompted any required adjustments. This loop was repeated across the project—for example, when refining the deterministic provenance implementation in `ProvenanceService.kt`—so the implementation evolved through deliberate review rather than a single generation pass.

Within each phase, the agent translated the prompt into implementation tasks, wrote code and tests, ran the test suite, inspected failures, modified the implementation, and repeated that cycle until the tests passed. This workflow covered both feature development and verification: test failures were treated as feedback about incorrect behavior, integration gaps, or incomplete assumptions rather than as a final stopping point.

## Generative assistance

Generative assistance covered the implementation surface of the PoC, including:

- **Application scaffolding:** Micronaut project structure, Kotlin/JVM configuration, Gradle build setup, application configuration, dependency wiring, and the HTTP entry point.
- **Database definition and fixtures:** `schema.sql` DDL for the five operational tables, foreign-key relationships, status constraints, nullable staff assignment, and deterministic mock seed data for clients, programs, staff, case statuses, and service activities.
- **API and transport models:** Request/response DTOs, query status handling, structured provenance records, JSON serialization annotations, and the `POST /api/v1/query` controller contract.
- **AI service layer:** LangChain4j declarative `@AiService` interfaces, structured intent and answer decisions, Ollama configuration, and system-prompt constraints for read-only parameterized SQL, ambiguity handling, refusal boundaries, and grounded answer synthesis.
- **Data-access and grounding logic:** JDBC query execution with prepared-statement parameter binding, result snapshots, primary-key discovery through JDBC database metadata, aggregate contributor queries, and programmatic provenance mapping.
- **Application orchestration:** The service flow coordinating intent parsing, `EXECUTE`/`AMBIGUOUS`/`REFUSE` branching, database retrieval, provenance calculation, and synthesis of an answer from retrieved rows.
- **Verification assets:** JUnit 5 and Micronaut Test skeletons and scenarios covering controller behavior, database execution, ambiguity and refusal paths, SQL parameterization, and deterministic provenance behavior.

## Human oversight and architectural refactorings

- **Architectural pivot:** Raw HTTP completion calls were refactored into LangChain4j declarative `@AiService` interfaces for cleaner domain abstraction.
- **Parser rules refinement:** Code review caught naive SQL parsing. The system prompt was updated so active-case queries strictly filter on `case_statuses.status = 'Active'`.
- **Deterministic grounding enforcement:** Record provenance (`status_id`, `activity_id`, and `client_id`) is extracted programmatically in Kotlin directly from JDBC `ResultSet` metadata and returned rows, rather than being trusted to the LLM.
- **Test quality and bug review:** Tests were reviewed to ensure they make sense, exercise core functionality, and validate the important success, ambiguity, refusal, SQL execution, and provenance paths. Human review also checked the implementation and test failures iteratively to identify and resolve bugs rather than accepting generated code without verification.

# 6. Important Trade-offs, Limitations & Next Steps

## Important trade-offs

- **System-prompt schema injection vs. dynamic schema pruning (RAG for schemas):** Supplying the schema in the system prompt keeps this PoC simple and transparent, while enterprise deployments may need dynamic schema pruning or schema-specific RAG to control prompt size, tenancy, permissions, and schema evolution.
- **In-memory single-node H2 engine vs. distributed relational storage:** H2 provides deterministic, fast, zero-setup execution for a PoC. Production workloads require durable, highly available relational storage with transaction management, backups, scaling, and operational controls.
- **Code-derived provenance extraction vs. LLM text citations:** Kotlin derives provenance from JDBC `ResultSet` metadata, primary keys, and returned rows. This is more deterministic and auditable than asking an LLM to generate record citations, but it requires queries to expose source-table primary keys and adds lineage-processing logic to the service.

## Solution limitations

- **Complex joins:** Local LLM reasoning may be unreliable for complex queries involving four or more table joins.
- **Runtime SQL safety:** Safety currently relies on parameterized prepared statements rather than AST-based parsers that explicitly block write operations at the syntax-tree level.
- **Conversational context:** The service does not maintain multi-turn conversational context memory; each query is processed independently.
- **Explicit domain model:** The code does not currently encode domain knowledge through domain classes that define clear domain boundaries and relationships. This was a deliberate time-conscious compromise for the challenge and is acceptable for a PoC, but a production system must introduce an explicit domain model to make those boundaries and relationships clear, enforceable, and maintainable.

## Next Steps (Production Roadmap)

- Add an authentication workflow, as well as row-level security (RLS) and role-based access control (RBAC) for frontline client privacy.
- Implement dynamic schema retrieval using vectorized DDL indexing for large enterprise databases.
- Add connection pooling with HikariCP and query caching with Redis.
- Build an automated Text-to-SQL evaluation benchmark suite.
- Make the project fully non-blocking with Kotlin Coroutines and R2DBC. In the current implementation, each request-handling thread remains blocked until the LLM and database responses return; a reactive non-blocking design would free those threads to do more work and improve scalability.
- Dockerize the service.
