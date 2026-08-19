CREATE TABLE dealerships (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    timezone VARCHAR(64) NOT NULL
);

CREATE TABLE customers (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    email VARCHAR(320) NOT NULL
);

CREATE TABLE vehicles (
    id UUID NOT NULL PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers (id),
    vin VARCHAR(32) NOT NULL,
    registration VARCHAR(16) NOT NULL,
    make VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL
);

CREATE TABLE service_types (
    id UUID NOT NULL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    duration_minutes INT NOT NULL,
    required_skill VARCHAR(64) NOT NULL
);

CREATE TABLE dealership_service_types (
    dealership_id UUID NOT NULL REFERENCES dealerships (id),
    service_type_id UUID NOT NULL REFERENCES service_types (id),
    PRIMARY KEY (dealership_id, service_type_id)
);

CREATE TABLE service_bays (
    id UUID NOT NULL PRIMARY KEY,
    dealership_id UUID NOT NULL REFERENCES dealerships (id),
    name VARCHAR(64) NOT NULL
);

CREATE TABLE bay_capabilities (
    service_bay_id UUID NOT NULL REFERENCES service_bays (id),
    capability VARCHAR(64) NOT NULL,
    PRIMARY KEY (service_bay_id, capability)
);

CREATE TABLE technicians (
    id UUID NOT NULL PRIMARY KEY,
    dealership_id UUID NOT NULL REFERENCES dealerships (id),
    name VARCHAR(200) NOT NULL
);

CREATE TABLE technician_skills (
    technician_id UUID NOT NULL REFERENCES technicians (id),
    skill VARCHAR(64) NOT NULL,
    PRIMARY KEY (technician_id, skill)
);

CREATE TABLE appointments (
    id UUID NOT NULL PRIMARY KEY,
    dealership_id UUID NOT NULL REFERENCES dealerships (id),
    customer_id UUID NOT NULL REFERENCES customers (id),
    vehicle_id UUID NOT NULL REFERENCES vehicles (id),
    service_type_id UUID NOT NULL REFERENCES service_types (id),
    technician_id UUID NOT NULL REFERENCES technicians (id),
    service_bay_id UUID NOT NULL REFERENCES service_bays (id),
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    status VARCHAR(32) NOT NULL,
    cancelled_at TIMESTAMP
);

CREATE INDEX idx_appointments_dealership_start ON appointments (dealership_id, start_at);
CREATE INDEX idx_appointments_bay_start_end ON appointments (service_bay_id, start_at, end_at);
CREATE INDEX idx_appointments_tech_start_end ON appointments (technician_id, start_at, end_at);
CREATE INDEX idx_bays_dealership ON service_bays (dealership_id);
CREATE INDEX idx_technicians_dealership ON technicians (dealership_id);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(128) NOT NULL PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    appointment_id UUID,
    status_code INT NOT NULL,
    response_json CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL
);
