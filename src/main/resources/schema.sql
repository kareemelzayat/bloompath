-- BloomPath operational data model and deterministic PoC seed data.

CREATE TABLE IF NOT EXISTS clients (
    client_id VARCHAR(36) PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    date_of_birth DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS programs (
    program_id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT
);

CREATE TABLE IF NOT EXISTS staff (
    staff_id VARCHAR(36) PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS case_statuses (
    status_id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(36) NOT NULL,
    program_id VARCHAR(36) NOT NULL,
    assigned_staff_id VARCHAR(36),
    status VARCHAR(50) NOT NULL CHECK (status IN ('Active', 'On Hold', 'Pending Review', 'Completed')),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (client_id) REFERENCES clients(client_id),
    FOREIGN KEY (program_id) REFERENCES programs(program_id),
    FOREIGN KEY (assigned_staff_id) REFERENCES staff(staff_id)
);

CREATE TABLE IF NOT EXISTS service_activities (
    activity_id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(36) NOT NULL,
    program_id VARCHAR(36) NOT NULL,
    staff_id VARCHAR(36) NOT NULL,
    activity_type VARCHAR(100) NOT NULL,
    activity_date TIMESTAMP NOT NULL,
    notes TEXT NOT NULL,
    is_flagged BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (client_id) REFERENCES clients(client_id),
    FOREIGN KEY (program_id) REFERENCES programs(program_id),
    FOREIGN KEY (staff_id) REFERENCES staff(staff_id)
);

INSERT INTO clients (client_id, full_name, date_of_birth) VALUES
    ('CLI-101', 'John Doe', DATE '1990-04-12'),
    ('CLI-102', 'Maria Garcia', DATE '1987-09-23'),
    ('CLI-103', 'Aisha Patel', DATE '1995-01-17'),
    ('CLI-104', 'Robert Chen', DATE '1979-11-05'),
    ('CLI-105', 'John Smith', DATE '1989-06-30');

INSERT INTO programs (program_id, name, description) VALUES
    ('PRG-001', 'Youth Outreach', 'Support and mentoring for young adults.'),
    ('PRG-002', 'Housing Support', 'Housing stability and placement assistance.'),
    ('PRG-003', 'Employment Readiness', 'Training and support for job placement.');

INSERT INTO staff (staff_id, full_name, role, email) VALUES
    ('STF-001', 'Sarah Jenkins', 'Case Manager', 'sarah.jenkins@bloompath.example'),
    ('STF-002', 'David Okafor', 'Housing Specialist', 'david.okafor@bloompath.example'),
    ('STF-003', 'Emily Nguyen', 'Program Coordinator', 'emily.nguyen@bloompath.example');

INSERT INTO case_statuses (status_id, client_id, program_id, assigned_staff_id, status, updated_at) VALUES
    ('CAS-001', 'CLI-101', 'PRG-001', 'STF-001', 'Active', TIMESTAMP '2026-01-10 09:00:00'),
    ('CAS-002', 'CLI-102', 'PRG-001', 'STF-001', 'Active', TIMESTAMP '2026-01-11 10:30:00'),
    ('CAS-003', 'CLI-103', 'PRG-001', 'STF-001', 'On Hold', TIMESTAMP '2026-01-12 14:15:00'),
    ('CAS-004', 'CLI-104', 'PRG-002', 'STF-002', 'Active', TIMESTAMP '2026-01-13 08:45:00'),
    ('CAS-005', 'CLI-105', 'PRG-002', NULL, 'Pending Review', TIMESTAMP '2026-01-14 11:20:00'),
    ('CAS-006', 'CLI-102', 'PRG-002', 'STF-002', 'Active', TIMESTAMP '2026-01-15 13:00:00'),
    ('CAS-007', 'CLI-103', 'PRG-002', 'STF-001', 'Completed', TIMESTAMP '2026-01-15 13:00:00');

INSERT INTO service_activities (activity_id, client_id, program_id, staff_id, activity_type, activity_date, notes, is_flagged) VALUES
    ('ACT-001', 'CLI-101', 'PRG-001', 'STF-001', 'Intake', TIMESTAMP '2026-01-05 09:00:00', 'Initial intake completed.', FALSE),
    ('ACT-002', 'CLI-101', 'PRG-001', 'STF-001', 'Follow-up', TIMESTAMP '2026-01-20 15:30:00', 'Follow-up completed and next appointment scheduled.', FALSE),
    ('ACT-003', 'CLI-102', 'PRG-002', 'STF-002', 'Housing Check-in', TIMESTAMP '2026-01-21 10:00:00', 'Client has not yet submitted updated housing documents.', TRUE),
    ('ACT-004', 'CLI-103', 'PRG-002', 'STF-002', 'Housing Check-in', TIMESTAMP '2026-01-22 11:15:00', 'Follow-up on placement referral is incomplete.', TRUE),
    ('ACT-005', 'CLI-104', 'PRG-002', 'STF-002', 'Counseling', TIMESTAMP '2026-01-23 13:45:00', 'Discussed housing search options.', FALSE),
    ('ACT-006', 'CLI-105', 'PRG-002', 'STF-002', 'Intake', TIMESTAMP '2026-01-24 09:30:00', 'Initial housing support intake completed.', FALSE),
    ('ACT-007', 'CLI-102', 'PRG-001', 'STF-001', 'Counseling', TIMESTAMP '2026-01-25 16:00:00', 'Discussed education goals.', FALSE);
