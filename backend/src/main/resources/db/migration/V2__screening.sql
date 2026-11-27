-- Screening state is owned by this application. No provider calls, secrets, or sample people.
ALTER TABLE portal_users ADD CONSTRAINT portal_users_organization_user UNIQUE (organization_id, id);
CREATE TABLE screening_packages (
    id uuid PRIMARY KEY,
    name varchar(160) NOT NULL CHECK (length(trim(name)) > 0),
    description varchar(1000) NOT NULL,
    purpose varchar(20) NOT NULL CHECK (purpose IN ('EMPLOYMENT', 'TENANT')),
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE organization_screening_packages (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    package_id uuid NOT NULL REFERENCES screening_packages(id),
    active boolean NOT NULL DEFAULT true,
    UNIQUE (organization_id, package_id),
    UNIQUE (organization_id, id)
);

CREATE TABLE screening_applicants (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    first_name varchar(100) NOT NULL CHECK (length(trim(first_name)) > 0),
    last_name varchar(100) NOT NULL CHECK (length(trim(last_name)) > 0),
    email varchar(254) NOT NULL CHECK (email = lower(trim(email)) AND length(email) > 0),
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL,
    CONSTRAINT screening_applicants_actor_same_company FOREIGN KEY (organization_id, created_by) REFERENCES portal_users(organization_id, id),
    UNIQUE (organization_id, id)
);
CREATE INDEX screening_applicants_company_created ON screening_applicants(organization_id, created_at DESC, id DESC);

CREATE TABLE screening_consents (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    request_id uuid NOT NULL,
    applicant_id uuid NOT NULL,
    purpose varchar(20) NOT NULL CHECK (purpose IN ('EMPLOYMENT', 'TENANT')),
    authorization_reference varchar(512) NOT NULL CHECK (length(trim(authorization_reference)) > 0),
    attestation_text varchar(1000) NOT NULL CHECK (length(trim(attestation_text)) > 0),
    evidence_type varchar(30) NOT NULL CHECK (evidence_type = 'REFERENCE_ONLY'),
    source varchar(30) NOT NULL CHECK (source = 'OPERATOR_ATTESTATION'),
    recorded_at timestamptz NOT NULL,
    recorded_by uuid NOT NULL,
    CONSTRAINT screening_consents_actor_same_company FOREIGN KEY (organization_id, recorded_by) REFERENCES portal_users(organization_id, id),
    FOREIGN KEY (organization_id, applicant_id) REFERENCES screening_applicants(organization_id, id),
    UNIQUE (organization_id, request_id),
    UNIQUE (organization_id, id, applicant_id)
);
CREATE INDEX screening_consents_applicant_recorded ON screening_consents(organization_id, applicant_id, recorded_at DESC, id DESC);

CREATE TABLE screening_orders (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    request_id uuid NOT NULL,
    applicant_id uuid NOT NULL,
    applicant_name varchar(201) NOT NULL CHECK (length(trim(applicant_name)) > 0),
    package_assignment_id uuid NOT NULL,
    package_name varchar(160) NOT NULL CHECK (length(trim(package_name)) > 0),
    consent_id uuid NOT NULL,
    status varchar(20) NOT NULL CHECK (status = 'QUEUED'),
    created_at timestamptz NOT NULL,
    created_by uuid NOT NULL,
    CONSTRAINT screening_orders_actor_same_company FOREIGN KEY (organization_id, created_by) REFERENCES portal_users(organization_id, id),
    UNIQUE (organization_id, request_id),
    FOREIGN KEY (organization_id, applicant_id) REFERENCES screening_applicants(organization_id, id),
    FOREIGN KEY (organization_id, package_assignment_id) REFERENCES organization_screening_packages(organization_id, id),
    FOREIGN KEY (organization_id, consent_id, applicant_id) REFERENCES screening_consents(organization_id, id, applicant_id)
);
CREATE INDEX screening_orders_company_created ON screening_orders(organization_id, created_at DESC, id DESC);

