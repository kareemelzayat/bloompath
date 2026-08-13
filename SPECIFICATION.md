# Technical Specification: Grounded Operational QA Service (BloomPath PoC)

## 1. Project Overview
This service provides an accurate, grounded, and explainable Question-Answering (QA) API over BloomPath's operational frontline dataset. The service translates natural language questions into deterministic database queries, enforces data boundary guardrails (handling ambiguity and refusing out-of-scope requests), and returns structured responses paired with explicit record-level provenance.

---

## 2. Architecture & Technology Stack

### Stack Definition
* **Language:** Kotlin (JVM 25)
* **Framework:** Micronaut (Kotlin flavor)
* **Database Engine:** H2 In-Memory Database (`jdbc:h2:mem:bloompath`)
* **Persistence Layer:** Micronaut Data JDBC
* **Serialization:** Micronaut Serialization (Jackson)
* **LLM Engine:** Local Ollama API
* **Testing:** JUnit 5 + Micronaut Test

### End-to-End Execution Flow
```
[Client Request: POST /api/v1/query]
         │
         ▼
[Step 1: Intent Parsing & Classification (LLM Call 1)]
  │ Parses query into JSON: action ("execute" | "ambiguous" | "refuse")
  │ Generates parameterized SQL query & params if action == "execute"
         │
         ├───────────────────────┬───────────────────────┐
         ▼                       ▼                       ▼
   (action: refuse)     (action: ambiguous)      (action: execute)
         │                       │                       │
         │                       │                       ▼
         │                       │        [Step 2: SQL Execution (H2 Database)]
         │                       │          │ Runs parameterized query against H2
         │                       │          │ Returns raw ResultRow list
         │                       │                       │
         │                       │                       ▼
         │                       │        [Step 3: Programmatic Provenance Extraction]
         │                       │          │ Extracts primary keys & tables directly
         │                       │          │ from returned ResultRows (Deterministic)
         │                       │                       │
         │                       │                       ▼
         │                       │        [Step 4: Answer Synthesis (LLM Call 2)]
         │                       │          │ Generates clear text answer using ONLY
         │                       │          │ the retrieved SQL rows
         │                       │                       │
         └───────────────────────┴───────────────────────┘
                                 │
                                 ▼
               [Step 5: Return Structured JSON Response]
```

---

## 3. Database Schema (DDL)

```sql
-- Core Entities
CREATE TABLE clients (
    client_id VARCHAR(36) PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    dob DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE programs (
    program_id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT
);

CREATE TABLE staff (
    staff_id VARCHAR(36) PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL
);

-- Case Management Junction (State Machine)
CREATE TABLE case_statuses (
    status_id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(36) NOT NULL,
    program_id VARCHAR(36) NOT NULL,
    assigned_staff_id VARCHAR(36), -- Nullable for unassigned cases
    status VARCHAR(50) NOT NULL CHECK (status IN ('Active', 'On Hold', 'Pending Review', 'Completed')),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (client_id) REFERENCES clients(client_id),
    FOREIGN KEY (program_id) REFERENCES programs(program_id),
    FOREIGN KEY (assigned_staff_id) REFERENCES staff(staff_id)
);

-- Operational Activity Log (Event Stream)
CREATE TABLE service_activities (
    activity_id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(36) NOT NULL,
    program_id VARCHAR(36) NOT NULL,
    staff_id VARCHAR(36) NOT NULL,
    activity_type VARCHAR(100) NOT NULL, -- Enum: Counseling, Housing Check-in, Intake, Follow-up
    activity_date TIMESTAMP NOT NULL,
    notes TEXT NOT NULL,
    is_flagged BOOLEAN DEFAULT FALSE, -- Identifies uncompleted/flagged follow-ups
    FOREIGN KEY (client_id) REFERENCES clients(client_id),
    FOREIGN KEY (program_id) REFERENCES programs(program_id),
    FOREIGN KEY (staff_id) REFERENCES staff(staff_id)
);
```

---

## 4. API Contract & Output Data Classes (Kotlin)

### Endpoint
`POST /api/v1/query`

### Kotlin DTO Contracts

```kotlin
import io.micronaut.core.annotation.Introspected

@Serdeable
data class QueryRequest(
    val question: String
)

@Serdeable
data class ProvenanceRecord(
    val table: String,
    val recordId: String,
    val fieldsUsed: List<String>
)

@Serdeable
data class QueryResponse(
    val status: QueryStatus,
    val question: String,
    val answer: String,
    val provenance: List<ProvenanceRecord> = emptyList(),
    val clarificationPrompt: String? = null
)

enum class QueryStatus {
    SUCCESS, AMBIGUOUS, REFUSED
}
```

---

## 5. Requirements & Edge Case Specifications

The test suite and application must demonstrate explicit handling of the following five core test scenarios:

### Question Type 1: Metric / Aggregation Query
* **Input Question:** *"How many active cases are assigned to Sarah Jenkins in Youth Outreach?"*
* **Expected Outcome:** `status: SUCCESS`. Returns the integer count, names of assigned clients, and primary key provenance mapping to the exact `case_statuses` rows queried.

### Question Type 2: Temporal / History Query
* **Input Question:** *"What was the last service activity recorded for Client CLI-101?"*
* **Expected Outcome:** `status: SUCCESS`. Returns activity type, date, notes, and provenance mapping to `service_activities.activity_id`.

### Question Type 3: Cross-Entity Filter Query
* **Input Question:** *"List all clients in Housing Support who have flagged or uncompleted notes."*
* **Expected Outcome:** `status: SUCCESS`. Performs a multi-table JOIN across `clients`, `case_statuses`, and `service_activities` where `is_flagged = true`.

### Ambiguous Question Handling
* **Input Question:** *"What was John's last activity?"*
* **Expected Outcome:** `status: AMBIGUOUS`. Detector finds multiple clients matching "John" (e.g., John Doe vs. John Smith).
* **Response payload:** `clarificationPrompt: "Multiple clients matched 'John'. Did you mean John Doe (CLI-101) or John Smith (CLI-105)?"`

### Unanswerable / Refusal Query
* **Input Question:** *"What is the annual operating budget for the Housing Support program?"*
* **Expected Outcome:** `status: REFUSED`. Classifier detects financial/budget attributes outside the schema.
* **Response payload:** `answer: "I cannot answer this question. Financial and budget data are not tracked in BloomPath operational records."`, `provenance: []`.

---

## 6. Project Directory Structure (Kotlin / Gradle / Micronaut)

```
.
├── README.md
├── SPECIFICATION.md
├── build.gradle.kts
├── settings.gradle.kts
└── src/
    ├── main/
    │   ├── kotlin/
    │   │   └── com/bloompath/
    │   │       ├── Application.kt                   # Micronaut Main Entrypoint
    │   │       ├── controller/
    │   │       │   └── QueryController.kt          # @Controller POST /api/v1/query
    │   │       ├── dto/
    │   │       │   ├── QueryRequest.kt
    │   │       │   └── QueryResponse.kt
    │   │       ├── service/
    │   │       │   ├── QaEngineService.kt          # Orchestration pipeline logic
    │   │       │   ├── IntentParserService.kt               # OpenAI API Client / Structured Parser
                    ├── IntentParserService.kt               # OpenAI API Client / Structured Parser
    │   │       │   └── DatabaseQueryService.kt    # Parameterized H2 execution & provenance extraction
    │   │       └── config/
    │   │           └── DatabaseInitializer.kt      # Seeds H2 on startup (@EventListener / StartupEvent)
    │   └── resources/
    │       ├── application.yml                      # Micronaut & H2 configuration
    │       └── schema.sql                           # Database DDL & Seed Data SQL
    └── test/
        ├── kotlin/
        │   └── com/bloompath/
        │       ├── QueryControllerTest.kt           # MicronautTest REST E2E tests
        │       └── QaEngineServiceTest.kt          # Unit tests for 5 key scenarios
        └── resources/
            └── application-test.yml
```

---

## 7. Implementation Guidelines for Codex

1. **Deterministic Grounding in Kotlin:** Extract primary key IDs from JDBC `ResultSet` programmatically into `ProvenanceRecord` objects. Never let the LLM synthesize or guess record IDs.
2. **Micronaut Seed Initialization:** Use a `@Singleton` bean with a `@EventListener(StartupEvent::class)` method to automatically execute `schema.sql` and populate seed records into H2 on boot.
3. **Structured OpenAI JSON Parsing:** Utilize Jackson with Kotlin module (`jackson-module-kotlin`) or Kotlinx Serialization to cleanly parse LLM JSON responses into strong-typed Kotlin Data Classes.
4. **Parameterized SQL:** All dynamic queries generated by the parser must be executed using `PreparedStatement` parameter bindings in JDBC/Jdbi to maintain full protection against SQL injection.