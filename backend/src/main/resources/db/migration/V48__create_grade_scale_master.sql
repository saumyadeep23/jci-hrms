-- New, additive grade-scale system (Board/Executive/Staff cadre, 15-grade hierarchy) alongside the
-- existing pay_scale_master/PayScale table - NOT a replacement. pay_scale_master is left untouched:
-- it is still read by PayrollComputationService (DA/HRA/EPF), LastPayCertificateService,
-- EmployeeServiceBookService, IncrementProcessingService, MovementOrderService, JoiningReportService,
-- EmployeeOnboardingService, EmployeeService, PromotionOrderPdfGenerator, and a live V32 SQL view -
-- retiring it is a separate, later, carefully-planned migration once those dependents are moved over
-- (see session discussion), not something this migration attempts.
CREATE TABLE grade_scale_master (
    id                     BIGSERIAL PRIMARY KEY,
    scale_code             VARCHAR(10)  NOT NULL,
    cadre                  VARCHAR(20)  NOT NULL,
    hierarchy_level        INT          NOT NULL,
    is_board_level         BOOLEAN      NOT NULL DEFAULT FALSE,
    minimum_basic          NUMERIC(12,2) NOT NULL,
    maximum_basic          NUMERIC(12,2) NOT NULL,
    increment_rate         NUMERIC(5,2)  NOT NULL DEFAULT 3.00,
    scale_type             VARCHAR(10)   NOT NULL DEFAULT 'IDA',
    effective_date         DATE          NOT NULL DEFAULT '2017-01-01',
    -- Left NULL at seed time - PUT /api/v1/masters/grade-scales/{id} (GradeScaleController) is how
    -- an admin fills these in per JCI's actual contractual/outsourced wage-settlement figures, not
    -- guessed here (mirrors PayrollRateProperties' own "not confirmed JCI policy" placeholder pattern).
    contractual_lumpsum    NUMERIC(12,2),
    outsourced_ctc         NUMERIC(12,2),
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_grade_scale_master_scale_code UNIQUE (scale_code),
    CONSTRAINT uq_grade_scale_master_hierarchy_level UNIQUE (hierarchy_level),
    CONSTRAINT ck_grade_scale_master_cadre CHECK (cadre IN ('BOARD', 'EXECUTIVE', 'STAFF')),
    CONSTRAINT ck_grade_scale_master_scale_type CHECK (scale_type IN ('IDA', 'CDA')),
    CONSTRAINT ck_grade_scale_master_basic_range CHECK (maximum_basic >= minimum_basic)
);

-- Board (E9/E8) and Executive (E7-E0): E6-E1 are not given explicit figures anywhere this migration
-- was written against (only E9/E8/E7 and E0 were) - interpolated evenly between the given E7/E0
-- endpoints, same placeholder spirit as PayrollRateProperties' HRA %, pending the real wage-revision
-- circular figures for those six grades.
INSERT INTO grade_scale_master (scale_code, cadre, hierarchy_level, is_board_level, minimum_basic, maximum_basic) VALUES
    ('E9', 'BOARD',     1, TRUE,  160000.00, 290000.00),
    ('E8', 'BOARD',     2, TRUE,  120000.00, 280000.00),
    ('E7', 'EXECUTIVE', 3, FALSE, 100000.00, 260000.00),
    ('E6', 'EXECUTIVE', 4, FALSE,  90000.00, 240000.00),
    ('E5', 'EXECUTIVE', 5, FALSE,  80000.00, 220000.00),
    ('E4', 'EXECUTIVE', 6, FALSE,  70000.00, 200000.00),
    ('E3', 'EXECUTIVE', 7, FALSE,  60000.00, 180000.00),
    ('E2', 'EXECUTIVE', 8, FALSE,  50000.00, 160000.00),
    ('E1', 'EXECUTIVE', 9, FALSE,  40000.00, 140000.00),
    ('E0', 'EXECUTIVE', 10, FALSE, 30000.00, 120000.00),
    ('S5', 'STAFF',     11, FALSE, 28600.00, 115000.00),
    ('S4', 'STAFF',     12, FALSE, 23000.00,  92500.00),
    ('S3', 'STAFF',     13, FALSE, 21500.00,  86500.00),
    ('S2', 'STAFF',     14, FALSE, 20000.00,  80500.00),
    ('S1', 'STAFF',     15, FALSE, 19000.00,  76500.00);

-- Optional designation -> grade-scale link (nullable - existing designation rows are unaffected,
-- an admin assigns a grade via the Designations tab as needed). Backs AddMovementOrderModal's
-- promotion-eligibility filter (target.hierarchyLevel < current.hierarchyLevel, isBoardLevel=false).
ALTER TABLE designations ADD COLUMN grade_scale_id BIGINT REFERENCES grade_scale_master (id);

-- The order's own intended effective date, distinct from order_date (when it was issued) and from
-- the movement record's own joining_date (when the employee actually reported - set later via the
-- joining-report flow). Nullable - existing/simple orders need not set it.
ALTER TABLE movement_orders ADD COLUMN effective_date DATE;

-- AddMovementOrderModal's "TO Station" dropdown lets an admin pick either a Regional/Head Office or
-- a DPC. from_office_id/to_office_id stay NOT NULL and every existing office-dependent code path
-- (PayrollMovementIntegrationService's HRA/city-class lookup, JoiningReportService's soft-GPS
-- geofence check, all four PDF generators) is untouched: when a DPC is picked, the frontend/service
-- resolves it to its own parent RO (dpc_master.ro_id) for office_id, and these two columns just carry
-- which DPC (if any) that resolution came from, purely for accurate display - nullable, additive,
-- read by nothing else.
ALTER TABLE employee_movement_records ADD COLUMN from_dpc_id BIGINT REFERENCES dpc_master (id);
ALTER TABLE employee_movement_records ADD COLUMN to_dpc_id BIGINT REFERENCES dpc_master (id);
