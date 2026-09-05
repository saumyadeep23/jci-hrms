# TASK SPECIFICATION: Enterprise HRMS - PIMS (Personnel Information Management System) Module

## 1. Context & Architecture Overview
You are building the production PIMS module for the JCI Enterprise Human Resource Management System (HRMS) on PostgreSQL 16+.
The system follows a strict 3NF normalized architecture, separating Sanctioned Posts from Employee Masters, enforcing temporal bank account lifecycles (SCD Type-2), 4-tier employment governance, and dynamic API enrichments.

### Existing Master Tables Reference
- `public.departments` (`id`, `code`, `name`, `short_code`, `cadre_identity`)
- `public.designations` (`id`, `code`, `title`, `department_id`, `category_type`, `grade`, `scale_grade`)
- `public.pay_scale_master` (`id`, `designation_id`, `designation_code`, `scale_type`, `grade`, `minimum_basic`, `maximum_basic`, `increment_rate`)
- `public.ro_master` (`id`, `ro_code`, `ro_name`, `office_type`, `state`, `city_class`, `latitude`, `longitude`, `geofence_radius_meters`)
- `public.dpc_master` (`id`, `dpc_code`, `dpc_name`, `ro_id`, `dpc_type`, `state`, `district`, `pin_code`, `city_class`)
- `public.state_details` & `public.district_details`

### Database Entities to Implement / Integrate
- Core: `public.employees`, `public.post_master`, `public.post_incumbency`
- Satellites:
  - `public.manpower_vendor_master` (Outsourced contractor agencies)
  - `public.employee_employment_categories` (4-Tier governance: Regular, Casual, Contractual, Outsourced)
  - `public.outsourced_salary_breakdown` (CTC line items)
  - `public.employee_bank_accounts` (SCD Type-2 temporal banking ledger)
  - `public.employee_addresses` (Dynamic postal API compliant)
  - `public.employee_qualifications` (Academic & professional credentials)
  - `public.employee_past_service_records` (Prior PSU/Govt experience with qualifying service flag)
  - `public.employee_family_details`, `public.employee_dependents`, `public.employee_nominees`
  - `public.employee_social_profiles`, `public.employee_recruitment_details`
  - `public.employee_superannuation_details` (58 yrs Regular / 60 yrs Director rules)
  - `public.employee_documents`, `public.audit_logs`

---

## 2. Core Functional Requirements to Implement

### Feature 1: Atomic `employee_code` & `personnel_no` Generator
- Implement an atomic database service `generateNextEmployeeCode(dbClient)`:
  - Finds the current highest numeric value:
    ```sql
    SELECT COALESCE(
        MAX(SUBSTRING(employee_code FROM '[0-9]+')::INTEGER), 
        0
    ) + 1 AS next_code_num
    FROM public.employees
    WHERE employee_code ~ '^[0-9]+$';
    ```
  - Pads `employee_code` as 4 digits (e.g., `0001`, `0104`, `1246`).
  - Formats `personnel_no` as `EMP` + 6 digits (e.g., `EMP001246`).
  - Wraps generation inside an explicit PostgreSQL transaction with table-level row/advisory locking to prevent concurrency collisions.

---

### Feature 2: Post Master Record Management (`/admin/posts` & `/api/v1/posts`)
- **Sanctioned Seat Inventory**:
  - `GET /api/v1/posts`: Paginated listing with search and filters by `department_id`, `designation_id`, `ro_id`, `dpc_id`, and `vacancy_status` (`VACANT`, `OCCUPIED`, `FROZEN`, `ABOLISHED`).
  - `GET /api/v1/posts/vacant`: Returns only budgeted, vacant posts eligible for regular assignment.
  - `POST /api/v1/posts`: Creates a new sanctioned seat with unique `post_code` (e.g., `POST-HO-IT-001`), title, linkages, and self-referencing reporting hierarchy (`operational_reporting_post_id`, `administrative_reporting_post_id`, `accepting_authority_post_id`).
  - `PUT /api/v1/posts/:id`: Updates post configuration or status (Freeze, Abolish).
- **Concurrency & Vacancy Rules**:
  - Enforce `uq_idx_single_regular_active_occupant` (Partial unique index ensuring only ONE active Regular occupant per post).
  - Database trigger `trg_sync_post_vacancy` automatically toggles `vacancy_status` between `'OCCUPIED'` and `'VACANT'` upon `post_incumbency` changes and blocks assignments to frozen/abolished posts.

---

### Feature 3: 8-Step Mobile-Responsive Onboarding Wizard (`/onboarding/new`)
Build a mobile-first wizard optimized for viewports from 360px smartphones up to desktop screens with step indicators, field validation, and draft capability.

#### Step 1: Personal & Bio-Data
- Salutation (`Mr.`, `Ms.`, `Mrs.`, `Dr.`), First Name, Middle Name, Last Name (auto-computes preview `full_name`).
- Date of Birth (must be `< CURRENT_DATE`; auto-computes age and displays live retirement preview).
- Gender, Marital Status, Blood Group, Nationality (`Indian`), Mother Tongue.
- PAN (regex: `^[A-Z]{5}[0-9]{4}[A-Z]$`), Aadhaar Ref (12 digits, stored masked `XXXX-XXXX-1234`).
- Personal Mobile (10 digits), Personal Email, Official Email (`@jcimail.in`).

#### Step 2: Dynamic Address (India Post API)
- Present & Permanent address fields.
- **PIN Code Integration**: On entering 6-digit PIN (`onBlur`), call `https://api.postalpincode.in/pincode/{PIN}`:
  - Auto-populates `State` and `District`.
  - Dynamically populates Post Office (`PO`) dropdown options.
- Checkbox: *"Permanent address same as present address"*.

#### Step 3: Dynamic Banking & Disbursal (IFSC API + SCD Type-2)
- Bank Account Number & Re-enter Account Number match validation.
- **IFSC Integration**: On entering 11-digit IFSC (`onBlur`), auto-fetch Bank Name and Branch Name.
- Upload cancelled cheque copy / passbook image.
- Creates active record in `employee_bank_accounts` (trigger archives older accounts as `HISTORICAL`).

#### Step 4: Academic & Professional Qualifications (`employee_qualifications`)
- Dynamic repeater cards (Add/Remove qualification):
  - Level (`10TH_SECONDARY`, `12TH_HIGHER_SECONDARY`, `DIPLOMA`, `GRADUATION`, `POST_GRADUATION`, `DOCTORATE_PHD`, `PROFESSIONAL_CERTIFICATION`).
  - Degree Title, Specialization, Board/University, Institution, Passing Year, Percentage/CGPA, Division.
  - Certificate file upload.

#### Step 5: Past Employment & Prior Service (`employee_past_service_records`)
- Dynamic repeater cards for past experience:
  - Organization Name, Organization Type (`CENTRAL_GOVT`, `STATE_GOVT`, `CENTRAL_PSU`, `STATE_PSU`, `AUTONOMOUS_BODY`, `PRIVATE_SECTOR`).
  - Designation Held, From Date, To Date (auto-calculates service days).
  - Last Pay Scale Pattern (IDA/CDA), Last Drawn Basic.
  - Checkbox: `is_qualifying_for_pension_gratuity` (Triggers past service reckoning calculation).
  - Experience / Relieving NOC upload.

#### Step 6: 4-Tier Employment Category & Post Assignment
- Radio selection for Employment Tier:
  1. **`REGULAR`**: Select vacant `post_id` (auto-fetches Designation and Department), select IDA `pay_scale_id`, enter Basic Pay.
  2. **`CASUAL`**: Enter approved Daily Wage Rate (INR/day) and wage order ref.
  3. **`CONTRACTUAL`**: Enter Monthly Fixed Lump-Sum, Contract Start Date, Contract End Date, Order Ref.
  4. **`OUTSOURCED`**: Select Vendor Agency from `manpower_vendor_master`, enter Monthly CTC, Billing Rate, Agency Employee ID, and CTC breakdown line items.
- Date of Joining PSU, Appointment Letter Number & Date, Joining Letter Date.

#### Step 7: Family, Dependents & Nominees
- Father Name, Mother Name, Spouse Name & Spouse DOB (if married).
- Dependents table (Name, Relationship, DOB, Gender, Medical Insurance Coverage flag).
- Nominee table (Nominee for: PF / Gratuity / NPS, Nominee Name, Relationship, Share % summing to exactly 100%).

#### Step 8: Document Verification & Final Review
- Upload: Photo, Signature, PAN Card, Aadhaar Card, Caste/PwBD Certificate, Appointment Letter.
- Full accordion review summary of all 8 steps.

---

### Feature 4: Draft Saving, Resumption & Finalization Engine
- **Draft Persistence Architecture**:
  - Sticky bottom action bar available on every step:
    - **"Save as Draft"**: Bypasses mandatory validations; persists partial payload into `employees` with `status = 'DRAFT'`.
    - **"Submit & Complete Onboarding"**: Executes full schema validation across all 8 steps, allocates sequential `employee_code` & `personnel_no`, creates `post_incumbency`, toggles post to `'OCCUPIED'`, and updates `status = 'ACTIVE'`.
- **Backend Draft Endpoints**:
  - `POST /api/v1/onboarding/draft`: Creates/updates draft record.
  - `GET /api/v1/onboarding/drafts`: Lists incomplete drafts with completion percentage.
  - `GET /api/v1/onboarding/drafts/:id`: Hydrates wizard with saved JSON payload.
  - `POST /api/v1/onboarding/drafts/:id/finalize`: Atomically validates and activates employee.

---

### Feature 5: Superannuation Engine
- Trigger `trg_calc_jci_superannuation` on `employees`:
  - **Regular Staff**: Auto-calculates retirement at **58 years** (last day of month of 58th birthday; if born on 1st, last day of preceding month).
  - **Board Directors** (MD, CMD, DF, DM): Auto-calculates retirement at **60 years OR 5 years from appointment**, whichever is earlier.
- Stored procedure `sp_apply_director_ministry_extension(employee_id, order_no, order_date)`: Applies Ministry tenure extension up to age 60.

---

### Feature 6: Dynamic Authority Discovery Engine (APAR View)
- Expose view `public.vw_apar_routing_matrix`: Resolves Reporting, Reviewing, and Accepting officers by querying the active physical occupants of supervisor posts.
- Function `fn_generate_apar_instances_for_cycle(cycle_id)`: Generates dual-charge APAR instances for officers holding Additional Charge posts for $\ge 90$ days.

---

## 3. API Route Specifications

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/posts` | List sanctioned posts with filters |
| `GET` | `/api/v1/posts/vacant` | List vacant budgeted posts for regular assignment |
| `POST` | `/api/v1/posts` | Create sanctioned post inventory seat |
| `GET` | `/api/v1/geo/pincode/:pincode` | Proxy India Post API (State, District, Post Offices) |
| `GET` | `/api/v1/finance/ifsc/:ifsc` | Lookup IFSC for Bank Name and Branch |
| `POST` | `/api/v1/onboarding/draft` | Save partial onboarding draft |
| `GET` | `/api/v1/onboarding/drafts` | List all pending drafts |
| `GET` | `/api/v1/onboarding/drafts/:id` | Rehydrate onboarding wizard with draft data |
| `POST` | `/api/v1/onboarding/drafts/:id/finalize` | Validate, allocate codes, and activate employee |
| `GET` | `/api/v1/employees/:id/360` | Full unified 360 profile from `vw_jci_employee_master_360` |

---

## 4. UI/UX & Responsive Guidelines
- Mobile-first design using Tailwind CSS (`w-full sm:w-1/2 lg:w-1/3`, touch-friendly inputs, sticky bottom bar).
- Real-time inline field validation using Zod / Joi / Spring Validation.
- Progress bar displaying step completion percentage.
- Toast notifications on successful draft saves and informative error banners on validation failure.