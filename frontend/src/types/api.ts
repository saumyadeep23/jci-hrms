// Hand-written mirrors of the backend's Java DTOs (in.gov.jci.hrms.dto.*).

// --- ALMS Reporting Workbench (AlmsReportController, /api/v1/reports/alms) ---
export interface MusterRollRow {
  employeeId: number
  employeeCode: string
  employeeName: string
  designation: string
  officeName: string
  state: string | null
  dailyPunches: Record<string, string>
  totalCycleDays: number
  presentDays: number
  paidLeaveDays: number
  weeklyOffsAndHolidays: number
  unpaidLwpDays: number
  penaltyDeductionDays: number
  netPayableDays: number
}

export interface PayrollCutoffFeedRow {
  employeeId: number
  employeeCode: string
  employeeName: string
  periodStart: string
  periodEnd: string
  totalCycleDays: number
  payableDays: number
  lwpDays: number
  absentDays: number
  penaltyDays: number
  approvedInServiceElDays: number
  financeOrderRef: string | null
  isLocked: boolean
}

export interface Circular53ComplianceRow {
  employeeId: number
  employeeCode: string
  employeeName: string
  officeName: string
  monthYear: string
  flexGraceCount: number
  concessionLateCount: number
  concessionEarlyCount: number
  thirdStrikeUnregularizedCount: number
  penaltyLeaveDebited: number
}

export interface CcsForm1Transaction {
  fromDate: string
  toDate: string
  type: string
  enjoyableDebited: number
  encashableDebited: number
  orderRef: string | null
}

export interface CcsForm1Response {
  employeeId: number
  employeeCode: string
  employeeName: string
  designation: string
  calendarYear: number
  openingEnjoyable: number
  openingEncashable: number
  advanceCreditEnjoyable: number
  advanceCreditEncashable: number
  eolDeduction: number
  availedTransactions: CcsForm1Transaction[]
  closingEnjoyable: number
  closingEncashable: number
  totalBalance: number
  surplusBuffer: number
}

export interface EncashmentRegisterRow {
  applicationNumber: number
  employeeCode: string
  employeeName: string
  encashmentType: string
  qualifyingTenure: string
  daysClaimed: number
  hrApprovedBy: string | null
  hrApprovedAt: string | null
  financeApprovedBy: string | null
  financeApprovedAt: string | null
  isPayrollEligible: boolean
  serviceBookFolio: number | null
  estimatedAmount: number | null
}
// Field names/shapes are kept in exact sync with those records - see the
// corresponding *.java file noted above each block if the backend changes.

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

// --- Attendance (MobilePunchRequest/Response) ---
export type PunchType = 'IN' | 'OUT'
export type ReviewStatus = 'VALID' | 'FLAGGED_FOR_REVIEW'

export interface MobilePunchRequest {
  /** Optional - MobilePunchController derives it from the JWT's employee_id claim when omitted. */
  employeeId?: number
  punchTime: string
  punchType: PunchType
  latitude: number
  longitude: number
  accuracyMeters?: number
  deviceId?: string
  photoS3Key?: string
}

export interface MobilePunchResponse {
  id: number
  employeeId: number
  employeeCode: string
  punchTime: string
  punchType: PunchType
  latitude: number
  longitude: number
  accuracyMeters: number | null
  isWithinGeofence: boolean
  reviewStatus: ReviewStatus
  deviceId: string | null
  photoS3Key: string | null
  createdAt: string
}

// --- Attendance aggregation engine (AttendanceAggregationController, Phase B/C) ---
// Supersedes the old non-persisted /attendance/my-history view (still a real,
// tested backend endpoint - AttendanceHistoryController/DailyAttendanceSummaryResponse
// on the Java side - just no longer called from this frontend now that
// AttendanceDetailTable renders from the persisted, authoritative engine instead).
// The persisted, authoritative counterpart - one row per evaluated day, with
// the JCI-circular grace/concession pipeline's fine-grained status.
export type AttendanceDetailStatus =
  | 'IN_PROGRESS'
  | 'PRESENT'
  | 'GRACE_APPLIED'
  | 'LATE_SHORT_HOURS'
  | 'REQUIRES_REGULARIZATION'
  | 'UNAUTHORIZED_LATE'
  | 'HALF_DAY_PRESENT'
  | 'HALF_DAY_SHORT'
  | 'HALF_DAY_ABSENT'
  | 'ON_LEAVE'
  | 'HOLIDAY'
  | 'WEEKOFF'
  | 'ABSENT'
  | 'ON_TOUR'

export interface DailyAttendanceDetailResponse {
  date: string
  inTime: string | null
  outTime: string | null
  totalWorkingHours: number | null
  detailStatus: AttendanceDetailStatus
  remarks: string | null
  leaveApplicationId: number | null
  leaveTypeCode: string | null
  tourRequestId: number | null
  tourRequestNumber: string | null
  tourDestination: string | null
}

// --- Employee (PIMS_SPEC.md) ---
export type Salutation = 'MR' | 'MS' | 'MRS' | 'DR'
export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'PREFER_NOT_TO_SAY'
export type MaritalStatus = 'SINGLE' | 'MARRIED' | 'WIDOWED' | 'DIVORCED' | 'OTHER'
export type BloodGroup = 'A_POSITIVE' | 'A_NEGATIVE' | 'B_POSITIVE' | 'B_NEGATIVE' | 'AB_POSITIVE' | 'AB_NEGATIVE' | 'O_POSITIVE' | 'O_NEGATIVE'
export type EmployeeStatus =
  | 'ACTIVE'
  | 'ON_PROBATION'
  | 'ON_LEAVE'
  | 'SUSPENDED'
  | 'RETIRED'
  | 'RESIGNED'
  | 'DECEASED'
  | 'INACTIVE'
  | 'TERMINATED'

export interface EmployeeResponse {
  id: number
  employeeCode: string
  cpfAcNo: string | null
  salutation: Salutation
  firstName: string
  middleName: string | null
  lastName: string
  fullName: string
  gender: Gender
  dateOfBirth: string
  maritalStatus: MaritalStatus
  bloodGroup: BloodGroup | null
  nationality: string
  motherTongue: string | null
  panNumber: string
  aadhaarRefNumber: string | null
  personalEmail: string
  officialEmail: string | null
  phone: string
  officialMobile: string | null
  dateOfJoining: string
  departmentId: number
  departmentName: string
  designationId: number
  designationTitle: string
  roId: number | null
  roName: string | null
  dpcId: number | null
  dpcName: string | null
  payScaleId: number | null
  payScaleGrade: string | null
  status: EmployeeStatus
  geofenceExempted: boolean
  /** "HEAD_OFFICE" | "REGIONAL_OFFICE" | "WAREHOUSE" | "DPC" | "SUB_DPC" - computed server-side, null if neither RO nor DPC is set. */
  officeType: string | null
  createdAt: string
  updatedAt: string
  /** Only populated by GET /employees/{id} (not the list endpoint, to avoid an N+1 query per row) - drives frontend gating like the ESS Sidebar's e-Service Book link (REGULAR only). */
  employmentCategory: EmploymentCategory | null
  /** Only populated by GET /employees/{id} - DB-trigger-maintained (fn_calculate_jci_superannuation_date, V31/V53), plain ISO like every other date on this DTO (format via formatDate() for display). */
  superannuationDate: string | null
  /** Only populated by GET/PUT /employees/{id} - the employee's active SUBSTANTIVE post_incumbency, if any (see EmployeeService.resolveCurrentPost). */
  postId: number | null
  postCode: string | null
  postTitle: string | null
  /** Pension & Retirement Schemes (V55). A REGULAR employee defaults to NPS; EPS-95 only applies when migrated in from EPS-covered past employment. */
  isNpsEligible: boolean
  isEpsEligible: boolean
  /** Only meaningful (and only ever true) when isEpsEligible is also true - see EmployeeService.applyOptionalFields. */
  isEpsHigherPensionEligible: boolean
  /** NPS Permanent Retirement Account Number (12 digits) - only meaningful when isNpsEligible. */
  pranNumber: string | null
}

export interface EmployeeRequest {
  salutation: Salutation
  firstName: string
  middleName?: string | null
  lastName: string
  gender: Gender
  dateOfBirth: string
  maritalStatus: MaritalStatus
  bloodGroup?: BloodGroup | null
  nationality: string
  motherTongue?: string | null
  panNumber: string
  /** Employee's real Contributory Provident Fund account number - client-supplied, same as panNumber. */
  cpfAcNo: string
  /** Raw 12-digit Aadhaar - the backend masks it to "XXXX-XXXX-1234" before storing. */
  aadhaarNumber?: string | null
  personalEmail: string
  officialEmail?: string | null
  phone: string
  officialMobile?: string | null
  dateOfJoining: string
  departmentId: number
  designationId: number
  roId?: number | null
  dpcId?: number | null
  payScaleId?: number | null
  status: EmployeeStatus
  geofenceExempted: boolean
  /** Sanctioned Post (post_master) assignment - null means "no change" on create and "release the current post" on update. See EmployeeService.syncPostAssignment. */
  postId?: number | null
  /** Pension & Retirement Schemes (V55) - defaults true when omitted (CPSE Defined Contribution / NPS). */
  isNpsEligible?: boolean | null
  /** Defaults false when omitted (EPS-95/EPFO only applies to employees migrated in from EPS-covered past employment). */
  isEpsEligible?: boolean | null
  /** Force-cleared to false server-side whenever isEpsEligible is false - see EmployeeService.applyOptionalFields. */
  isEpsHigherPensionEligible?: boolean | null
  /** NPS Permanent Retirement Account Number (12 digits) - only meaningful when isNpsEligible. */
  pranNumber?: string | null
}

// --- Employee Address (EmployeeAddressController, PIMS_SPEC.md Step 2) ---
export type AddressType = 'PERMANENT' | 'PRESENT' | 'COMMUNICATION'

export interface EmployeeAddressRequest {
  addressType: AddressType
  addressLine1: string
  addressLine2?: string | null
  postOffice?: string | null
  policeStation?: string | null
  city: string
  district: string
  state: string
  pinCode: string
}

export interface EmployeeAddressResponse extends EmployeeAddressRequest {
  id: number
  employeeId: number
}

// --- Employee Bank Account (EmployeeBankAccountController, PIMS_SPEC.md Step 3, SCD Type-2) ---
export type BankAccountType = 'SAVINGS' | 'CURRENT' | 'SALARY'
export type BankAccountStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'HISTORICAL' | 'REJECTED'

export interface EmployeeBankAccountRequest {
  bankName: string
  bankBranch: string
  bankAccountNumber: string
  reenterBankAccountNumber: string
  bankIfsc: string
  cancelledChequeS3Key?: string | null
}

export interface EmployeeBankAccountResponse {
  id: number
  employeeId: number
  bankName: string
  bankBranch: string
  bankAccountNumber: string
  bankIfsc: string
  accountType: BankAccountType
  primaryDisbursal: boolean
  status: BankAccountStatus
  effectiveFrom: string
  effectiveTo: string | null
}

// --- Qualifications (EmployeeQualificationController, PIMS_SPEC.md Step 4) ---
export type QualificationLevel =
  | 'TENTH_SECONDARY'
  | 'TWELFTH_HIGHER_SECONDARY'
  | 'DIPLOMA'
  | 'GRADUATION'
  | 'POST_GRADUATION'
  | 'DOCTORATE_PHD'
  | 'PROFESSIONAL_CERTIFICATION'
  | 'OTHER'
export type DivisionClass = 'DISTINCTION' | 'FIRST_CLASS' | 'SECOND_CLASS' | 'PASS_CLASS' | 'GRADE_A' | 'GRADE_B' | 'GRADE_C'
export type CourseType = 'FULL_TIME' | 'PART_TIME' | 'DISTANCE_CORRESPONDENCE' | 'ONLINE'

export interface QualificationRequest {
  qualificationLevel: QualificationLevel
  degreeTitle: string
  specialization?: string | null
  boardUniversity: string
  institutionName?: string | null
  passingYear: number
  percentageCgpa?: number | null
  divisionClass?: DivisionClass | null
  courseType: CourseType
  highestQualification: boolean
  certificateDocumentS3Key?: string | null
}

export interface QualificationResponse extends QualificationRequest {
  id: number
  employeeId: number
  verified: boolean
  verifiedBy: string | null
  verifiedAt: string | null
}

// --- Past Service Records (EmployeePastServiceRecordController, PIMS_SPEC.md Step 5) ---
export type PastServiceOrganizationType =
  | 'CENTRAL_GOVT'
  | 'STATE_GOVT'
  | 'CENTRAL_PSU'
  | 'STATE_PSU'
  | 'AUTONOMOUS_BODY'
  | 'DEFENCE_ARMY_NAVY_AIRFORCE'
  | 'PRIVATE_SECTOR'
  | 'OTHER'
export type PastServicePayScalePattern = 'IDA' | 'CDA' | 'CONSOLIDATED' | 'OTHER'

export interface PastServiceRecordRequest {
  organizationName: string
  organizationType: PastServiceOrganizationType
  designationHeld: string
  fromDate: string
  toDate: string
  lastPayScalePattern?: PastServicePayScalePattern | null
  lastDrawnBasic?: number | null
  lastDrawnGross?: number | null
  qualifyingForPensionGratuity: boolean
  qualifyingServiceOrderRef?: string | null
  reasonForLeaving?: string | null
  experienceCertificateS3Key?: string | null
  relievingNocDocumentS3Key?: string | null
}

export interface PastServiceRecordResponse extends PastServiceRecordRequest {
  id: number
  employeeId: number
  totalServiceDays: number | null
  verified: boolean
  verifiedBy: string | null
  verifiedAt: string | null
}

// --- Onboarding wizard (EmployeeOnboardingController, PIMS_SPEC.md Features 3 & 4) ---
export type OnboardingStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'CANCELLED'
export type EmploymentCategory = 'REGULAR' | 'CASUAL' | 'CONTRACTUAL' | 'OUTSOURCED'
export type RecruitmentMode = 'DIRECT_RECRUITMENT' | 'PROMOTION' | 'DEPUTATION' | 'COMPASSIONATE' | 'ABSORPTION'
/** REGULAR only - which half-year cycle this employee's future annual increments fall on (regular_pay_fixations.increment_cycle, V50). */
export type IncrementCycle = 'JULY' | 'JANUARY'
export type SalaryBreakdownHeadType = 'EARNING' | 'DEDUCTION' | 'EMPLOYER_STATUTORY' | 'VENDOR_FEE'
export type DocumentCategory =
  | 'PHOTO'
  | 'SIGNATURE'
  | 'PAN_CARD'
  | 'AADHAAR'
  | 'CASTE_CERT'
  | 'PWBD_CERT'
  | 'APPOINTMENT_ORDER'
  | 'JOINING_REPORT'
  | 'SERVICE_BOOK_SCAN'
  | 'APAR'
  | 'DISCIPLINARY'
  | 'OTHER'

export interface OnboardingPersonalDetailsRequest {
  salutation: Salutation
  firstName: string
  middleName?: string | null
  lastName: string
  gender: Gender
  dateOfBirth: string
  maritalStatus: MaritalStatus
  bloodGroup?: BloodGroup | null
  nationality: string
  motherTongue?: string | null
  panNumber: string
  cpfAcNo: string
  aadhaarNumber?: string | null
  personalEmail: string
  officialEmail?: string | null
  phone: string
  officialMobile?: string | null
}

export interface OutsourcedSalaryBreakdownEntry {
  headCode: string
  headName: string
  headType: SalaryBreakdownHeadType
  amount: number
}

export interface OnboardingEmploymentStepRequest {
  employmentCategory: EmploymentCategory
  departmentId?: number | null
  designationId?: number | null
  // REGULAR
  postId?: number | null
  payScaleId?: number | null
  regularBasicPay?: number | null
  // CASUAL
  dailyWageRate?: number | null
  wageRevisionOrderNo?: string | null
  // CONTRACTUAL
  fixedLumpSumMonthly?: number | null
  contractStartDate?: string | null
  contractEndDate?: string | null
  contractRefOrder?: string | null
  // OUTSOURCED
  vendorId?: number | null
  monthlyCtc?: number | null
  billingRateMonthly?: number | null
  agencyEmployeeId?: string | null
  ctcBreakdown?: OutsourcedSalaryBreakdownEntry[] | null
  /** Optional additive link into grade_scale_master (V48/V49) - see OnboardingEmploymentStepRequest.scaleCode's backend javadoc. */
  scaleCode?: string | null
  /** REGULAR only - defaults JULY server-side when null. */
  incrementCycle?: IncrementCycle | null
  // Pension & Retirement Schemes (V55)
  isNpsEligible?: boolean | null
  isEpsEligible?: boolean | null
  isEpsHigherPensionEligible?: boolean | null
  pranNumber?: string | null
  // Recruitment metadata
  dateOfJoiningPsu: string
  recruitmentYear: number
  recruitmentMode: RecruitmentMode
  selectionMethod: string
  recruitmentAgency?: string | null
  advertisementNo?: string | null
  appointmentLetterNo: string
  appointmentLetterDate: string
  offerLetterDate?: string | null
  joiningLetterDate: string
}

export interface OnboardingDependentEntry {
  name: string
  relationship: string
  dateOfBirth?: string | null
  isDependent: boolean
  isCoveredMedical: boolean
}

export interface OnboardingNomineeEntry {
  name: string
  relationship: string
  sharePercentage: number
  /** Free text - typically "PF" / "Gratuity" / "NPS"; shares within each group must sum to exactly 100. */
  nomineeFor: string
}

export interface OnboardingFamilyStepRequest {
  fatherName: string
  motherName?: string | null
  spouseName?: string | null
  spouseDob?: string | null
  dependents?: OnboardingDependentEntry[] | null
  nominees?: OnboardingNomineeEntry[] | null
}

export interface OnboardingDocumentEntry {
  documentCategory: DocumentCategory
  documentTitle: string
  fileS3Key: string
  mimeType?: string | null
}

// --- Family / Dependents / Nominees for an already-onboarded employee (EmployeeFamilyController, EmployeeDependentController, EmployeeNomineeController) ---
export interface FamilyDetailsRequest {
  fatherName: string
  motherName?: string | null
  spouseName?: string | null
  spouseDob?: string | null
}

export interface FamilyDetailsResponse extends FamilyDetailsRequest {
  id: number
  employeeId: number
  dependentCount: number
}

export interface DependentRequest {
  employeeId: number
  name: string
  relationship: string
  dateOfBirth?: string | null
  isDependent: boolean
  isCoveredMedical: boolean
}

export interface DependentResponse extends DependentRequest {
  id: number
  createdAt: string
  updatedAt: string
}

export interface NomineeRequest {
  employeeId: number
  name: string
  relationship: string
  sharePercentage: number
  nomineeFor: string
}

export interface NomineeResponse extends NomineeRequest {
  id: number
  createdAt: string
  updatedAt: string
}

// --- Social Profile / Reservation Data (operational-features task, Section 1) ---
export type SocialCategory = 'GEN' | 'SC' | 'ST' | 'OBC_NCL' | 'EWS' | 'OTHER'

export interface SocialProfileRequest {
  socialCategory: SocialCategory
  subCasteCommunity?: string | null
  isPwbd: boolean
  disabilityType?: string | null
  disabilityPercentage?: number | null
}

export interface SocialProfileResponse extends SocialProfileRequest {
  id: number
  employeeId: number
}

export interface OnboardingSocialProfileRequest {
  socialCategory: SocialCategory
  subCasteCommunity?: string | null
  isPwbd: boolean
  disabilityType?: string | null
  disabilityPercentage?: number | null
  isExServiceman: boolean
  isSportsQuota: boolean
}

export interface OnboardingDraftUpsertRequest {
  draftId: number | null
  currentStep?: number | null
  personal?: OnboardingPersonalDetailsRequest | null
  presentAddress?: EmployeeAddressRequest | null
  permanentAddress?: EmployeeAddressRequest | null
  permanentSameAsPresent?: boolean | null
  banking?: EmployeeBankAccountRequest | null
  qualifications?: QualificationRequest[] | null
  pastServiceRecords?: PastServiceRecordRequest[] | null
  employment?: OnboardingEmploymentStepRequest | null
  family?: OnboardingFamilyStepRequest | null
  documents?: OnboardingDocumentEntry[] | null
  socialProfile?: OnboardingSocialProfileRequest | null
}

export interface OnboardingDraftResponse {
  id: number
  draftCode: string
  employeeCode: string
  currentStep: number
  status: OnboardingStatus
  completionPercentage: number
  personal: OnboardingPersonalDetailsRequest | null
  presentAddress: EmployeeAddressRequest | null
  permanentAddress: EmployeeAddressRequest | null
  permanentSameAsPresent: boolean | null
  banking: EmployeeBankAccountRequest | null
  qualifications: QualificationRequest[] | null
  pastServiceRecords: PastServiceRecordRequest[] | null
  employment: OnboardingEmploymentStepRequest | null
  family: OnboardingFamilyStepRequest | null
  documents: OnboardingDocumentEntry[] | null
  socialProfile: OnboardingSocialProfileRequest | null
  submittedEmployeeId: number | null
  createdAt: string
  updatedAt: string
  submittedAt: string | null
}

export interface OnboardingSubmitResponse {
  draftId: number
  draftCode: string
  employeeId: number
  employeeCode: string
}

// --- Post Master (PostMasterController, PIMS_SPEC.md Feature 2) ---
export type VacancyStatus = 'VACANT' | 'OCCUPIED' | 'FROZEN' | 'ABOLISHED'

export interface PostMasterResponse {
  id: number
  postCode: string
  title: string
  departmentId: number
  departmentName: string
  designationId: number
  designationTitle: string
  roId: number | null
  roName: string | null
  dpcId: number | null
  dpcName: string | null
  operationalReportingPostId: number | null
  operationalReportingPostTitle: string | null
  administrativeReportingPostId: number | null
  administrativeReportingPostTitle: string | null
  acceptingAuthorityPostId: number | null
  acceptingAuthorityPostTitle: string | null
  vacancyStatus: VacancyStatus
  isBudgeted: boolean
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface PostMasterRequest {
  postCode: string
  title: string
  departmentId: number
  designationId: number
  roId?: number | null
  dpcId?: number | null
  operationalReportingPostId?: number | null
  administrativeReportingPostId?: number | null
  acceptingAuthorityPostId?: number | null
  isBudgeted: boolean
  active: boolean
}

export interface PostStatusUpdateRequest {
  targetStatus: VacancyStatus
  remark?: string | null
}

export type AssignmentType = 'SUBSTANTIVE' | 'ADDITIONAL_CHARGE' | 'ACTING' | 'LOOK_AFTER'

export interface PostIncumbencyResponse {
  id: number
  postId: number
  postCode: string
  employeeId: number
  employeeCode: string
  assignmentType: AssignmentType
  startDate: string
  endDate: string | null
  orderReference: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface PostInventorySummaryResponse {
  totalSanctioned: number
  occupied: number
  vacant: number
  frozen: number
  abolished: number
}

/** POST /api/v1/posts/:id/incumbency - Dual/Additional Charge assignment (operational-features task, Section 2). */
export interface AssignIncumbencyRequest {
  employeeId: number
  assignmentType: AssignmentType
  startDate: string
  orderReference?: string | null
  orderDate?: string | null
}

// --- Document Upload (DocumentUploadController, PIMS_SPEC.md Section 3) ---
export type UploadCategory =
  | 'PHOTO'
  | 'SIGNATURE'
  | 'PAN_CARD'
  | 'AADHAAR'
  | 'CASTE_CERT'
  | 'PWBD_CERT'
  | 'BANK_PROOF'
  | 'QUALIFICATION'
  | 'PAST_SERVICE_NOC'
  | 'APPOINTMENT_ORDER'
  | 'JOINING_REPORT'
  | 'SERVICE_BOOK_SCAN'
  | 'APAR'
  | 'DISCIPLINARY'
  | 'OTHER'

export interface DocumentUploadResponse {
  fileS3Key: string
  documentCategory: UploadCategory
  originalFileName: string
  mimeType: string
  fileSizeBytes: number
  uploadedAt: string
}

export interface InvalidFilePayloadResponse {
  timestamp: string
  status: number
  errorCode: string
  message: string
  field: string
  allowedMimeTypes: string[]
  maxAllowedSizeBytes: number | null
}

// --- India Post pincode / IFSC lookup proxies (LookupController, FR-EMP.14) ---
export interface PincodeLookupResponse {
  pincode: string
  found: boolean
  district: string | null
  state: string | null
  message: string | null
  postOffices: string[]
}

export interface IfscLookupResponse {
  ifsc: string
  found: boolean
  bankName: string | null
  branch: string | null
  message: string | null
}

// --- Generic Master Data Admin (Section 1 of PIMS_SPEC admin GUIs task) ---
export interface DependencyUsage {
  table: string
  label: string
  activeCount: number
}

export interface DependencyCheckResponse {
  hasActiveDependencies: boolean
  usages: DependencyUsage[]
}

export interface MasterStatusUpdateRequest {
  active: boolean
}

// --- Master Data: Department (DepartmentController) ---
export interface DepartmentResponse {
  id: number
  code: string
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
  deletedAt: string | null
}

export interface DepartmentRequest {
  code: string
  name: string
  description?: string | null
}

// --- Master Data: Designation (DesignationController) ---
export interface DesignationResponse {
  id: number
  title: string
  description: string | null
  createdAt: string
  updatedAt: string
  deletedAt: string | null
  /** V48 (additive) - null until an admin assigns a grade scale via this tab. */
  gradeScaleId: number | null
  scaleCode: string | null
  cadre: Cadre | null
  hierarchyLevel: number | null
  boardLevel: boolean | null
  idaPayScale: string | null
  /** "Director" drives the 60-year/5-year-tenure superannuation rule (V31/V53) instead of the 58-year regular rule - see SuperannuationCalculator's javadoc. */
  categoryType: string | null
}

export interface DesignationRequest {
  title: string
  description?: string | null
  gradeScaleId?: number | null
}

// --- Master Data: Grade Scale Master (GradeScaleController, /api/v1/masters/grade-scales) ---
// New, additive Board/Executive/Staff 15-grade hierarchy alongside (not instead of) PayScale below -
// see GradeScaleMaster.java's javadoc for why pay_scale_master/PayScaleController were left untouched.
export type Cadre = 'BOARD' | 'EXECUTIVE' | 'STAFF'

export interface GradeScaleMasterResponse {
  id: number
  scaleCode: string
  cadre: Cadre
  hierarchyLevel: number
  boardLevel: boolean
  minimumBasic: number
  maximumBasic: number
  idaScaleLabel: string
  incrementRate: number
  scaleType: ScaleType
  /** Already formatted as "dd-MM-yyyy" by the backend (@JsonFormat) - render as-is, do NOT pass through formatDate() (which expects ISO yyyy-MM-dd and would mis-parse this). */
  effectiveDate: string
  contractualLumpsum: number | null
  outsourcedCtc: number | null
  active: boolean
}

/** POST /api/v1/masters/grade-scales - effectiveDate is plain ISO (yyyy-MM-dd) on this write side (unlike GradeScaleMasterResponse's pre-formatted read side) - send it through DatePicker's ISO output, never the response's dd-MM-yyyy string. */
export interface GradeScaleCreateRequest {
  scaleCode: string
  cadre: Cadre
  hierarchyLevel: number
  boardLevel: boolean
  minimumBasic: number
  maximumBasic: number
  incrementRate?: number | null
  effectiveDate: string
  contractualLumpsum?: number | null
  outsourcedCtc?: number | null
  active: boolean
}

/** PUT /api/v1/masters/grade-scales/{scaleCode} - same shape as GradeScaleCreateRequest minus scaleCode itself (path-supplied, immutable). */
export type GradeScaleUpdateRequest = Omit<GradeScaleCreateRequest, 'scaleCode'>

// --- Compensation ledgers (V50: regular_pay_fixations / contractual_engagements / outsourced_deployments) ---
// Renewal dates are strictly dd-MM-yyyy on the wire (both request and response) for this feature only
// - unlike GradeScaleCreateRequest above, convert DatePicker's ISO output via formatDate() before
// sending, never send the raw ISO value here.

/** POST /api/v1/employees/{id}/renew-contract */
export interface RenewContractRequest {
  monthlyLumpsum: number
  contractStartDate: string
  contractEndDate: string
  approvalRefNo: string
  scaleCode?: string | null
  engagementTerms?: string | null
}

export interface ContractualEngagementResponse {
  id: number
  employeeId: number
  scaleCode: string | null
  monthlyLumpsum: number
  contractStartDate: string
  contractEndDate: string
  approvalRefNo: string
  engagementTerms: string | null
  current: boolean
}

/** POST /api/v1/employees/{id}/renew-deployment */
export interface RenewDeploymentRequest {
  vendorId?: number | null
  monthlyCtc: number
  agencyBillingRate?: number | null
  deploymentStartDate: string
  deploymentEndDate: string
  workOrderRef: string
  scaleCode?: string | null
}

export interface OutsourcedDeploymentResponse {
  id: number
  employeeId: number
  vendorId: number | null
  vendorName: string | null
  scaleCode: string | null
  monthlyCtc: number
  agencyBillingRate: number | null
  deploymentStartDate: string
  deploymentEndDate: string
  workOrderRef: string
  current: boolean
}

// --- Master Data: Pay Scale (PayScaleController) ---
// ScaleType ('IDA' | 'CDA') is declared once below, alongside DaRateHistory - reused here as-is.
export interface PayScaleResponse {
  id: number
  scaleType: ScaleType
  grade: string
  minimumBasic: number
  maximumBasic: number
  incrementRate: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface PayScaleRequest {
  scaleType: ScaleType
  grade: string
  minimumBasic: number
  maximumBasic: number
  incrementRate: number
  active: boolean
}

// --- Master Data: Manpower Vendors (VendorMasterController) ---
export interface VendorMasterResponse {
  id: number
  vendorCode: string
  vendorName: string
  tradeName: string | null
  gstin: string | null
  panNumber: string | null
  epfRegistrationNo: string | null
  esicRegistrationNo: string | null
  contractStartDate: string
  contractEndDate: string
  contactPerson: string | null
  contactPhone: string | null
  contactEmail: string | null
  officeAddress: string | null
  serviceChargePercentage: number | null
  active: boolean
}

/** vendorCode is deliberately absent - it is system-generated on create and never accepted from the client (see VendorMasterRequest.java). */
export interface VendorMasterRequest {
  vendorName: string
  tradeName?: string | null
  gstin?: string | null
  panNumber?: string | null
  epfRegistrationNo?: string | null
  esicRegistrationNo?: string | null
  contractStartDate: string
  contractEndDate: string
  contactPerson?: string | null
  contactPhone?: string | null
  contactEmail?: string | null
  officeAddress?: string | null
  serviceChargePercentage?: number | null
  active: boolean
}

// --- Master Data: State / District (StateMasterController / DistrictMasterController) ---
export type StateType = 'STATE' | 'UNION_TERRITORY' | 'NATIONAL_CAPITAL_TERRITORY'

export interface StateMasterResponse {
  id: string
  stateCode: string
  stateName: string
  stateType: StateType
  active: boolean
  createdAt: string
}

export interface StateMasterRequest {
  stateCode: string
  stateName: string
  stateType: StateType
  active: boolean
}

export interface DistrictMasterResponse {
  id: string
  districtCode: string
  districtName: string
  stateId: string
  stateName: string
  active: boolean
  createdAt: string
}

export interface DistrictMasterRequest {
  districtCode: string
  districtName: string
  stateId: string
  active: boolean
}

// --- Master Data: Regional Office (RegionalOfficeController) ---
export type OfficeType = 'HEAD_OFFICE' | 'REGIONAL_OFFICE' | 'WAREHOUSE'
export type CityClass = 'X' | 'Y' | 'Z'

export interface RegionalOfficeResponse {
  id: number
  code: string
  name: string
  state: string
  cityClass: CityClass
  active: boolean
  officeType: OfficeType
  addressLine: string | null
  city: string | null
  district: string | null
  districtCode: string | null
  pinCode: string | null
  latitude: number | null
  longitude: number | null
  geofenceRadiusMeters: number
  recreationClubDeduction: number
  procurementAllowanceApplicable: boolean
  createdAt: string
  updatedAt: string
}

export interface RegionalOfficeRequest {
  code: string
  name: string
  state: string
  cityClass: CityClass
  active: boolean
  officeType: OfficeType
  addressLine?: string | null
  city?: string | null
  district?: string | null
  districtCode?: string | null
  pinCode?: string | null
  latitude?: number | null
  longitude?: number | null
  geofenceRadiusMeters?: number | null
  recreationClubDeduction?: number | null
  procurementAllowanceApplicable: boolean
}

// --- Master Data: DPC (DpcController) ---
export type DpcType = 'DPC' | 'SUB_DPC'

export interface DpcResponse {
  id: number
  roId: number
  roCode: string
  roName: string
  code: string
  name: string
  district: string
  state: string
  latitude: number | null
  longitude: number | null
  geofenceRadiusMeters: number | null
  active: boolean
  shortName: string | null
  dpcType: DpcType
  districtCode: string | null
  cityClass: CityClass
  createdAt: string
  updatedAt: string
}

export interface DpcRequest {
  roId: number
  code: string
  name: string
  district: string
  state?: string | null
  latitude?: number | null
  longitude?: number | null
  geofenceRadiusMeters?: number | null
  active: boolean
  shortName?: string | null
  dpcType: DpcType
  districtCode?: string | null
  cityClass: CityClass
}

// --- e-Service Book (ServiceBookEventResponse) ---
export interface ServiceBookEventResponse {
  id: number
  eventDate: string
  eventType: string
  orderNumber: string | null
  orderDate: string | null
  departmentName: string | null
  designationTitle: string | null
  regionalOfficeName: string | null
  basicPay: number | null
  eventDescription: string | null
  remarks: string | null
  isMigrated: boolean
}

/** Closed vocabulary for POST /api/v1/employees/:id/service-book - the read side (above) stays free-text for legacy-migrated entries. */
export type CareerEventType =
  | 'PROMOTION'
  | 'TRANSFER'
  | 'MACP'
  | 'PAY_REVISION'
  | 'PENALTY_WITHHOLD_INCREMENT'
  | 'PENALTY_CENSUROUS'
  | 'EOL_LWP'
  | 'EARNED_LEAVE_ENCASHABLE'
  | 'TRANSFER_RELEASE'
  | 'TRANSFER_JOINING'
  | 'TRANSFER_BENEFIT_EL_CREDIT'
  | 'PAY_FIXATION'

export interface ServiceBookEventRequest {
  eventDate: string
  eventType: CareerEventType
  orderNumber?: string | null
  orderDate?: string | null
  departmentId?: number | null
  designationId?: number | null
  regionalOfficeId?: number | null
  payScaleId?: number | null
  basicPay?: number | null
  remarks?: string | null
}

// --- Director Ministry Extension & Superannuation Calculator ---
export interface MinistryExtensionRequest {
  orderNumber: string
  orderDate: string
  extendedUptoDate?: string | null
  remarks?: string | null
}

export interface SuperannuationExtensionResponse {
  employeeId: number
  isMinistryExtended: boolean
  ministryExtensionOrderNo: string | null
  ministryExtensionOrderDate: string | null
  ministryExtendedUpto: string | null
  superannuationDate: string
  calculationBasis: string | null
}

export interface SuperannuationCalculationPreviewResponse {
  employeeId: number
  dateOfBirth: string
  regularSuperannuationDate58: string
  directorSuperannuationDate60: string
  director5YearTermDate: string | null
  actualSuperannuationDate: string | null
  actualCalculationBasis: string | null
  isBoardDirector: boolean
  isMinistryExtended: boolean
  ministryExtendedUpto: string | null
}

// --- Leave ---
/** FIRST_HALF/SECOND_HALF is CL-only (LeaveValidationService enforces that) - null/omitted on a request means FULL_DAY. */
export type LeaveSession = 'FULL_DAY' | 'FIRST_HALF' | 'SECOND_HALF'

export interface LeaveApplicationResponse {
  id: number
  employeeId: number
  employeeCode: string
  leaveTypeId: number
  leaveTypeCode: string
  startDate: string
  endDate: string
  totalDays: number
  reason: string
  status: string
  leaveSession: LeaveSession
  approverPostId: number | null
  approverEmployeeId: number | null
  /** Links a combined CL+RH pair (CombinedLeaveApplicationService) - null for a standalone application. */
  groupApplicationId: string | null
  rhEntryId: number | null
  debitedEnjoyableDays: number | null
  debitedEncashableDays: number | null
  createdAt: string
  updatedAt: string
}

export interface LeaveApplicationRequest {
  employeeId: number
  leaveTypeId: number
  startDate: string
  endDate: string
  totalDays: number
  reason: string
  leaveSession?: LeaveSession | null
}

// --- Leave types (LeaveTypeController, Phase A) ---
export interface LeaveTypeResponse {
  id: number
  code: string
  name: string
  annualQuota: number
  maxAccumulationDays: number | null
  isEncashable: boolean
  careerLimitDays: number | null
  active: boolean
  createdAt: string
  updatedAt: string
}

// --- Leave Type Master & Cadre Eligibility (LeaveTypeMasterController, /api/v1/master/leave-types) ---
export interface LeaveTypeMasterResponse {
  id: number
  code: string
  name: string
  maxAccumulationCap: number | null
  isEncashable: boolean
  isAccumulative: boolean
  active: boolean
  eligibleCategories: EmploymentCategory[]
}

export interface LeaveTypeMasterUpdateRequest {
  maxAccumulationCap: number | null
  isEncashable: boolean
  eligibleCategories: EmploymentCategory[]
}

// --- Device Registration (RegisteredDeviceController, /api/v1/attendance/devices) ---
export type DeviceType = 'ANDROID_MOBILE' | 'IOS_MOBILE' | 'LAPTOP_DESKTOP_WEB' | 'LAPTOP_DESKTOP_CLIENT' | 'BIOMETRIC_TERMINAL'
export type DeviceApprovalStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REVOKED'

export interface RegisteredDeviceResponse {
  id: number
  employeeId: number
  employeeCode: string
  employeeName: string
  officeName: string
  deviceName: string
  deviceType: DeviceType
  deviceIdentifier: string
  platform: string | null
  status: DeviceApprovalStatus
  approvedByName: string | null
  approvedAt: string | null
  createdAt: string
}

export interface DeviceRegistrationRequest {
  deviceName: string
  deviceType: DeviceType
  deviceIdentifier: string
  platform: string | null
}

export interface DeviceStatusUpdateRequest {
  status: DeviceApprovalStatus
}

// --- Leave balances (LeaveBalanceController, Phase E) ---
export interface LeaveBalanceResponse {
  id: number
  leaveTypeId: number
  leaveTypeCode: string
  leaveTypeName: string
  year: number
  creditedDays: number
  usedDays: number
  reservedDays: number
  availableDays: number
  updatedAt: string
}

// --- Leave application preview (LeaveApplicationController /preview, Phase D) ---
export interface LeaveApplicationPreviewRequest {
  employeeId: number
  leaveTypeId: number
  startDate: string
  endDate: string
  leaveSession?: LeaveSession | null
}

export interface LeaveApplicationPreviewResponse {
  calendarDays: number
  /** null when valid is false - no meaningful figure exists for a rejected date range. */
  debitableDays: number | null
  valid: boolean
  /** The CCS-rule violation text when valid is false, null otherwise. */
  message: string | null
}

// --- Leave ledger (LeaveLedgerEntryController, Phase C) ---
export interface LeaveLedgerEntryResponse {
  id: number
  employeeId: number
  employeeCode: string
  leaveTypeId: number
  leaveTypeCode: string
  entryDate: string
  deltaDays: number
  description: string
  source: LeaveLedgerSource
  relatedDailyAttendanceId: number | null
  relatedLeaveApplicationId: number | null
  createdAt: string
}

export type LeaveLedgerSource =
  | 'AUTO_LATE_DEDUCTION'
  | 'COMMUTED_LEAVE_HPL_DEBIT'
  | 'BASELINE_TAKEON'
  | 'EL_SEMI_ANNUAL_ACCRUAL'
  | 'EL_EOL_LAPSE_DEDUCTION'
  | 'EL_ENCASHMENT_DEBIT'
  | 'ATTENDANCE_PENALTY_REFUND'

// --- ALMS Phase 2/3: EL entitlement sub-ledger (LeaveEntitlementBalanceController) ---
export interface LeaveEntitlementBalanceResponse {
  id: number
  employeeId: number
  leaveTypeId: number
  leaveTypeCode: string
  year: number
  openingBalance: number
  creditedDays: number
  availedDays: number
  reservedDays: number
  encashedDays: number
  lapsedDays: number
  currentBalance: number
  availableBalance: number
  encashableOpening: number
  encashableCredited: number
  encashableAvailed: number
  encashableReserved: number
  encashableEncashed: number
  encashableCurrent: number
  encashableAvailable: number
  enjoyableOpening: number
  enjoyableCredited: number
  enjoyableAvailed: number
  enjoyableReserved: number
  enjoyableCurrent: number
  enjoyableAvailable: number
}

// --- ALMS Phase 2/3: baseline take-on (LeaveBaselineTakeOnController) ---
export interface BaselineTakeOnRequest {
  employeeId: number
  leaveTypeId: number
  asOnDate: string
  openingBalance: number
  openingEncashableEl?: number | null
  openingEnjoyableEl?: number | null
  physicalServiceBookFolio?: string | null
  verificationOrderRef?: string | null
}

export interface BaselineTakeOnResponse {
  baselineId: string
  employeeId: number
  employeeCode: string
  leaveTypeId: number
  leaveTypeCode: string
  asOnDate: string
  openingBalance: number
  openingEncashableEl: number
  openingEnjoyableEl: number
  physicalServiceBookFolio: string | null
  verificationOrderRef: string | null
  verifiedByEmployeeId: number | null
  locked: boolean
  createdAt: string
}

// --- ALMS Phase 2/3: combined CL+RH (CombinedLeaveController) ---
export interface CombinedLeaveApplicationRequest {
  employeeId: number
  clLeaveTypeId: number
  clStartDate: string
  clEndDate: string
  clSession?: LeaveSession | null
  rhLeaveTypeId: number
  rhDate: string
  rhSession?: LeaveSession | null
  rhHolidayId: number
  reason: string
}

export interface CombinedLeaveApplicationResponse {
  groupApplicationId: string
  clApplication: LeaveApplicationResponse
  rhApplication: LeaveApplicationResponse
}

// --- ALMS Phase 2/3: EL encashment (LeaveEncashmentController) ---
export type EncashmentType = 'IN_SERVICE_EL' | 'SUPERANNUATION' | 'SEPARATION'
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface LeaveEncashmentRequest {
  employeeId: number
  encashmentType: EncashmentType
  elDaysClaimed: number
  hplDaysClaimed?: number | null
}

export interface EncashmentGateDecisionRequest {
  approve: boolean
  remarks?: string | null
}

export interface LeaveEncashmentResponse {
  id: number
  employeeId: number
  employeeCode: string
  encashmentType: EncashmentType
  elDaysClaimed: number
  hplDaysClaimed: number
  hrApprovalStatus: ApprovalStatus
  hrApprovedByEmployeeId: number | null
  hrApprovedAt: string | null
  hrRemarks: string | null
  financeApprovalStatus: ApprovalStatus
  financeApprovedByEmployeeId: number | null
  financeApprovedAt: string | null
  financeRemarks: string | null
  payrollEligible: boolean
  serviceBookEntryId: number | null
  createdAt: string
  updatedAt: string
}

// --- ALMS Phase 2/3: attendance regularization (AttendanceRegularizationController) ---
/**
 * The real backend enum (AttendanceRegularizationApplicationRepository /
 * V36's ck_attendance_regularization_reason_code CHECK constraint) - not the
 * MISSED_IN_PUNCH/MISSED_OUT_PUNCH/LOCAL_OFFICIAL_DUTY/TECHNICAL_FAILURE/
 * PERSONAL_EMERGENCY set the Phase 3 spec names, which don't exist on the
 * backend and would fail every submission with a constraint violation.
 */
export type RegularizationReasonCode = 'FORGOT_PUNCH' | 'DEVICE_FAILURE' | 'FIELD_DUTY' | 'GEOFENCE_ISSUE' | 'SYSTEM_ERROR' | 'OTHER'

export interface AttendanceRegularizationRequest {
  employeeId: number
  attendanceDate: string
  reasonCode: RegularizationReasonCode
  remarks?: string | null
  correctedInTime: string
  correctedOutTime: string
}

export interface RegularizationDecisionRequest {
  approve: boolean
  remarks?: string | null
}

export interface AttendanceRegularizationResponse {
  id: number
  employeeId: number
  employeeCode: string
  attendanceDate: string
  dailyAttendanceId: number | null
  reasonCode: RegularizationReasonCode
  remarks: string | null
  correctedInTime: string
  correctedOutTime: string
  approvalStatus: ApprovalStatus
  designatedApproverId: number | null
  approvedAt: string | null
  approverRemarks: string | null
  createdAt: string
}

// --- Loans ---
export interface EmployeeLoanResponse {
  id: number
  loanTypeId: number
  loanTypeName: string
  employeeId: number
  employeeCode: string
  loanAccountNumber: string
  principalAmount: number
  interestRate: number
  totalInstallments: number
  remainingInstallments: number
  outstandingPrincipal: number
  status: string
  sanctionDate: string
  createdAt: string
  updatedAt: string
}

// --- APAR ---
export interface EmployeeAparResponse {
  id: number
  aparCycleId: number
  aparCycleYear: string
  employeeId: number
  employeeCode: string
  reportingOfficerId: number
  reviewingOfficerId: number
  acceptingAuthorityId: number
  status: string
  selfAppraisalText: string | null
  reportingScore: number | null
  reportingRemarks: string | null
  reviewingScore: number | null
  reviewingRemarks: string | null
  finalScore: number | null
  finalGrading: string | null
  representationText: string | null
  createdAt: string
  updatedAt: string
}

export interface AparCycleResponse {
  id: number
  cycleYear: string
  startDate: string
  endDate: string
  status: string
  createdAt: string
  updatedAt: string
}

// --- DA Rate History (DaRateHistoryController) ---
export type ScaleType = 'IDA' | 'CDA'

/** effectiveTo is server-computed (never client-supplied) - null means still current. */
export interface DaRateHistoryResponse {
  id: number
  scaleType: ScaleType
  effectiveFrom: string
  effectiveTo: string | null
  daPercentage: number
  active: boolean
  orderNumber: string | null
  orderDate: string | null
  remarks: string | null
}

export interface DaRateHistoryRequest {
  scaleType: ScaleType
  effectiveFrom: string
  daPercentage: number
  active: boolean
  orderNumber?: string | null
  orderDate?: string | null
  remarks?: string | null
}

// --- Payroll reporting (Phase 11) ---
export interface PfBucketSplitResponse {
  employeeEpf: number
  employerEpf: number
  employerEps: number
}

export interface AttendanceBreakdownResponse {
  totalCycleDays: number
  presentDays: number
  halfDays: number
  absentDays: number
  onLeaveDays: number
  holidayDays: number
  weeklyOffDays: number
  lopDays: number
}

export interface PayslipItemResponse {
  id: number
  salaryHeadId: number
  salaryHeadCode: string
  salaryHeadName: string
  amount: number
  causeRemarks: string | null
}

export interface PayslipSummaryResponse {
  payrollRunId: number
  cycleYear: number
  cycleMonth: number
  startDate: string
  endDate: string
  employeeId: number
  employeeCode: string
  employeeName: string
  basicPay: number
  totalEarnings: number
  totalDeductions: number
  employerContributions: number
  netPay: number
  taxDeductions: number
  attendance: AttendanceBreakdownResponse
  pfBucketSplit: PfBucketSplitResponse
  lineItems: PayslipItemResponse[]
}

export interface PayrollRunResponse {
  id: number
  cycleYear: number
  cycleMonth: number
  startDate: string
  endDate: string
  status: string
  finalizedBy: string | null
  finalizedAt: string | null
  runType: string
  isMigrated: boolean
  createdAt: string
}

// --- CPF ledger ---
export interface CpfBalanceLedgerResponse {
  employeeId: number
  employeeCode: string
  employeeFundBalance: number
  employerFundBalance: number
  pensionFundBalance: number
  lastDiversionDate: string | null
  updatedAt: string
}

// --- Disciplinary (Phase 11) ---
export interface DisciplinaryCaseResponse {
  id: number
  caseNumber: string
  employeeId: number
  employeeCode: string
  caseType: string
  status: string
  chargeSheetDate: string | null
  inquiryOfficerId: number | null
  inquiryOfficerCode: string | null
  penaltyType: string | null
  penaltyEffectiveFrom: string | null
  penaltyEffectiveTo: string | null
  remarks: string | null
  createdAt: string
  updatedAt: string
}

export interface DisciplinaryCaseRequest {
  caseNumber: string
  employeeId: number
  caseType: string
}

export interface DisciplinaryStageUpdateRequest {
  targetStatus: string
  chargeSheetDate?: string | null
  inquiryOfficerId?: number | null
  penaltyType?: string | null
  penaltyEffectiveFrom?: string | null
  penaltyEffectiveTo?: string | null
  remarks?: string | null
}

// --- Audit log (Phase 11) ---
export interface AuditLogResponse {
  id: number
  entityName: string
  entityId: number
  action: 'CREATE' | 'UPDATE' | 'DELETE'
  actingUsername: string | null
  clientIp: string | null
  beforeState: string | null
  afterState: string | null
  createdAt: string
}

// --- Legacy migration (Phase 9) ---
export interface RejectedRowSummary {
  stagingId: number
  identifier: string
  rejectionReason: string
}

export interface CategoryStatus {
  category: string
  pendingCount: number
  promotedCount: number
  rejectedCount: number
  rejectedRows: RejectedRowSummary[]
}

// --- Holidays (HolidayController) ---
export type HolidayType = 'GAZETTED' | 'RESTRICTED'

export interface HolidayResponse {
  id: number
  holidayDate: string
  name: string
  holidayType: HolidayType
  /** Null (or "CENTRAL") applies nationally/to every location. */
  state: string | null
  createdAt: string
  updatedAt: string
}

/** GET /holidays/my-calendar - the union of national, the caller's state-specific gazetted, and restricted holidays available at their posting location, for one calendar month. */
export interface HolidayCalendarResponse {
  officeLabel: string
  state: string | null
  holidays: HolidayResponse[]
  upcomingHolidayDate: string | null
  upcomingHolidayName: string | null
}

// --- State-wise Holiday & RH Management Publisher (HolidayMasterController, /api/v1/master/holidays) ---
export interface StateOptionResponse {
  stateCode: string
  stateName: string
}

/** stateCode "ALL" means national/Central scope (stateName "All India / Central"); otherwise the specific state code/name this row applies to. */
export interface HolidayMasterRow {
  id: number
  holidayDate: string
  holidayName: string
  holidayType: HolidayType
  isRestricted: boolean
  stateCode: string
  stateName: string
  description: string | null
}

/** stateCodes: ["ALL"] (or every currently-active state code) persists one national row; otherwise fans out into one row per code. */
export interface HolidayMasterCreateRequest {
  holidayName: string
  holidayDate: string
  holidayType: HolidayType
  stateCodes: string[]
  description: string | null
}

/** Edits one already-persisted (single-state) row - stateCode "ALL" re-scopes it to national. */
export interface HolidayMasterUpdateRequest {
  holidayName: string
  holidayDate: string
  holidayType: HolidayType
  stateCode: string
  description: string | null
}

export interface HolidayBulkUploadResult {
  totalRows: number
  createdRows: number
  errors: string[]
}

export interface LegacyMigrationStatusResponse {
  categories: CategoryStatus[]
}

// --- Employee 360 (EmployeeController.get360) ---
export interface Employee360Response {
  employeeId: number
  cpfAcNo: string | null
  employeeCode: string
  fullName: string
  salutation: string
  dateOfBirth: string
  age: number | null
  gender: string
  maritalStatus: string
  bloodGroup: string | null
  panNumber: string
  aadhaarRefNumber: string | null
  personalEmail: string
  officialEmail: string | null
  personalMobile: string
  officialMobile: string | null
  employmentStatus: string
  dateOfJoining: string
  employmentCategory: string | null
  compensationTierSummary: string | null
  regularBasicPay: number | null
  dailyWageRate: number | null
  fixedLumpSumMonthly: number | null
  monthlyCtc: number | null
  outsourcedVendorName: string | null
  currentPostId: number | null
  currentPostCode: string | null
  currentPostTitle: string | null
  currentAssignmentType: string | null
  postAssignmentStartDate: string | null
  departmentCode: string | null
  departmentName: string | null
  designationCode: string | null
  designationTitle: string | null
  scaleGrade: string | null
  roCode: string | null
  roName: string | null
  dpcCode: string | null
  dpcName: string | null
  activeBankName: string | null
  activeBankBranch: string | null
  activeBankAccountNo: string | null
  activeBankIfsc: string | null
  bankVerificationStatus: string | null
  socialCategory: string | null
  isPwbd: boolean | null
  disabilityType: string | null
  superannuationDate: string | null
  isBoardDirector: boolean | null
  superannuationCalculationBasis: string | null
  pensionSettlementStatus: string | null
}

// --- PIMS Reporting Hub (PimsReportController / IncrementController) ---
export interface PimsReportFilter {
  departmentId?: number | null
  designationId?: number | null
  roId?: number | null
  dpcId?: number | null
  employmentCategory?: string | null
  socialCategory?: string | null
  recruitmentMode?: string | null
  months?: number | null
  columns?: string[] | null
  search?: string | null
  /** Increment Due List's "Increment Month" filter (1-12, null = All Months) - matched against the employee's date-of-joining anniversary month. */
  incrementMonth?: number | null
}

export interface CadreStrengthRow {
  locationType: string
  sanctioned: number
  occupied: number
  vacant: number
  frozen: number
  occupancyPercentage: number
}

export interface CadreStrengthReportResponse {
  byLocation: CadreStrengthRow[]
  overall: CadreStrengthRow
}

export interface SuperannuationForecastEntry {
  employeeId: number
  employeeCode: string
  employeeName: string
  departmentName: string | null
  designationTitle: string | null
  dateOfBirth: string
  superannuationDate: string
  isBoardDirector: boolean
  calculationBasis: string | null
  monthsRemaining: number
}

export interface SuperannuationWindowSummary {
  months: number
  count: number
}

export interface SuperannuationReportResponse {
  windowSummary: SuperannuationWindowSummary[]
  entries: SuperannuationForecastEntry[]
}

export interface ReservationRosterRow {
  socialCategory: string
  recruitmentStream: string
  count: number
  representationPercentage: number
  statutoryTargetPercentage: number | null
}

export interface ReservationRosterReportResponse {
  rows: ReservationRosterRow[]
  pwbdCount: number
  pwbdPercentage: number
  totalEmployees: number
}

export interface ManpowerTierRow {
  employmentCategory: string
  costBasis: string
  headcount: number
  totalCost: number
  averageCost: number
}

export interface Manpower4TierReportResponse {
  tiers: ManpowerTierRow[]
  totalHeadcount: number
  totalOutsourcedVendorBilling: number
}

export interface AparMatrixRow {
  appraiseeEmployeeId: number
  appraiseeEmployeeCode: string
  appraiseeName: string
  postTitle: string
  assignmentType: string
  assignmentStartDate: string | null
  reportingOfficerName: string | null
  reviewingOfficerName: string | null
  acceptingOfficerName: string | null
  dualChargeOver90Days: boolean
}

export interface AparMatrixReportResponse {
  rows: AparMatrixRow[]
  dualChargeOver90DaysCount: number
}

export interface IncrementDueEntry {
  employeeId: number
  employeeCode: string
  employeeName: string
  payScaleGrade: string | null
  currentBasicPay: number
  incrementAmount: number
  newBasicPay: number
  atStagnationCeiling: boolean
  isWithheld: boolean
  withheldReason: string | null
}

export interface IncrementBatchProcessRequest {
  employeeIds: number[]
  orderNumber: string
  orderDate: string
  remarks?: string | null
}

export interface IncrementBatchProcessResponse {
  processedCount: number
  skippedCount: number
  skippedReasons: string[]
}

export interface TabularReportResponse {
  columns: string[]
  rows: Record<string, unknown>[]
  totalElements: number
}

export interface AdHocReportRequest {
  columns: string[]
  filter?: PimsReportFilter | null
  page?: number | null
  size?: number | null
}

export type ReportExportFormat = 'XLSX' | 'PDF'

export interface ReportExportRequest {
  reportType: string
  format: ReportExportFormat
  filter?: PimsReportFilter | null
}

// --- Functional & Statutory Role Management Subsystem (PIMS/ALMS) ---
export type RoleCategory = 'FUNCTIONAL' | 'STATUTORY' | 'GOVERNANCE' | 'TRUST'

export interface FunctionalRoleMasterResponse {
  id: string
  roleCode: string
  roleName: string
  roleCategory: RoleCategory
  hasFinancialDelegation: boolean
  hasAdministrativeDelegation: boolean
  active: boolean
  createdAt: string
}

export interface FunctionalRoleAssignmentResponse {
  id: string
  roleId: string
  roleCode: string
  roleName: string
  roleCategory: RoleCategory
  employeeId: number
  employeeCode: string
  employeeName: string
  designationTitle: string
  departmentId: number | null
  departmentName: string | null
  officeId: number | null
  officeName: string | null
  zoneCode: string | null
  officeOrderRef: string
  orderDate: string
  validFrom: string
  validTo: string | null
  isPrimaryRole: boolean
  active: boolean
  createdAt: string
}

export interface FunctionalRoleAssignmentRequest {
  roleId: string
  employeeId: number
  departmentId?: number | null
  officeId?: number | null
  zoneCode?: string | null
  officeOrderRef: string
  orderDate: string
  validFrom: string
  validTo?: string | null
  isPrimaryRole: boolean
}

// --- Shift Master (Master Data Console) ---
export type ShiftApplicableOfficeType = 'HEAD_OFFICE' | 'REGIONAL_OFFICE' | 'DPC'

export interface ShiftMasterResponse {
  id: number
  shiftCode: string
  shiftName: string
  /** "HH:mm:ss" (LocalTime) */
  startTime: string
  endTime: string
  gracePeriodMinutes: number
  crossesMidnight: boolean
  /** Minimum worked minutes to count as a full/half day - null if not configured for this shift. */
  fullDayMinutes: number | null
  halfDayMinutes: number | null
  /** Which office type this shift is meant for (informational only) - null for a shift not tied to one, e.g. a watchmen rotation. */
  applicableOfficeType: ShiftApplicableOfficeType | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface ShiftMasterRequest {
  shiftCode: string
  shiftName: string
  startTime: string
  endTime: string
  gracePeriodMinutes: number
  crossesMidnight: boolean
  fullDayMinutes?: number | null
  halfDayMinutes?: number | null
  applicableOfficeType?: ShiftApplicableOfficeType | null
  active: boolean
}

// --- Movement Lifecycle: Transfer / Promotion / Release / Joining Report (MovementLifecycleController, /api/v1/pims/movements) ---
export type MovementOrderType = 'TRANSFER' | 'PROMOTION' | 'TRANSFER_CUM_PROMOTION'
export type TransferNature = 'ADMINISTRATIVE' | 'OWN_REQUEST' | 'MUTUAL'
export type SessionType = 'FORENOON' | 'AFTERNOON'
export type MovementStatus = 'ORDERED' | 'RELIEVED' | 'JOINED'
export type JoiningStatus = 'NOT_SUBMITTED' | 'PENDING_VERIFICATION' | 'CLARIFICATION_REQUESTED' | 'ACCEPTED' | 'REJECTED'
export type PayrollSyncStatus = 'PENDING' | 'PROCESSED' | 'LPC_ISSUED' | 'LPC_ACCEPTED'

export interface EmployeeMovementRecordResponse {
  id: number
  orderId: number
  orderType: MovementOrderType
  orderRefNo: string
  orderDate: string
  orderEffectiveDate: string | null

  employeeId: number
  employeeName: string
  employeeCode: string

  transferNature: TransferNature
  transferBenefitAdmissible: boolean
  requestApplicationRef: string | null
  requestReason: string | null

  fromOfficeName: string
  fromDpcName: string | null
  fromDepartmentName: string | null
  fromDesignationTitle: string
  fromPayScale: string | null
  toOfficeName: string
  toDpcName: string | null
  toDepartmentName: string | null
  toDesignationTitle: string
  toPayScale: string | null
  stationDistanceKm: number
  promotionalBasicPay: number | null

  releaseOrderRef: string | null
  releaseDate: string | null
  releaseSession: SessionType | null
  releasedAtDbTimestamp: string | null

  joiningReportNo: string | null
  joiningDate: string | null
  joiningDbTimestamp: string | null
  joiningSession: SessionType | null
  joiningLatitude: number | null
  joiningLongitude: number | null
  joiningGpsAccuracy: number | null
  joiningDistanceMeters: number | null
  geoVerified: boolean
  joiningRemarks: string | null

  admissibleJtDays: number
  joiningTimeAvailedDays: number
  unavailedJtDays: number
  elCreditedDays: number
  elCredited: boolean

  probationPeriodMonths: number
  probationEndDate: string | null

  payrollSyncStatus: PayrollSyncStatus
  lpcNumber: string | null
  effectivePayFixationDate: string | null
  excessTransitLwpDays: number

  movementStatus: MovementStatus
  joiningStatus: JoiningStatus
  approvedByOfficerName: string | null
  approvedAt: string | null

  clarificationRemarks: string | null
  clarificationRequestedByName: string | null
  clarificationRequestedAt: string | null
  resubmissionCount: number
  resubmittedAt: string | null

  lpcIssueDate: string | null
  lpcSignatoryName: string | null
  lpcSignatoryDesignation: string | null
}

export interface ClarificationRequest {
  remarks: string
}

/** Each station (from/to) is exactly one of an office or a DPC - see MovementOrderCreateRequest.java's javadoc. */
export interface MovementOrderCreateRequest {
  orderType: MovementOrderType
  orderRefNo: string
  orderDate: string
  effectiveDate?: string | null
  sanctionedByRole?: string | null
  signedByEmployeeId?: number | null

  employeeId: number
  transferNature: TransferNature
  transferBenefitAdmissible: boolean
  requestApplicationRef?: string | null
  requestReason?: string | null

  fromOfficeId?: number | null
  fromDpcId?: number | null
  fromDepartmentId?: number | null
  fromDesignationId: number
  fromPayScale?: string | null
  toOfficeId?: number | null
  toDpcId?: number | null
  toDepartmentId?: number | null
  toDesignationId: number
  toPayScale?: string | null
  stationDistanceKmOverride?: number | null
  promotionalBasicPay?: number | null
  probationPeriodMonths?: number | null
}

export interface MovementReleaseRequest {
  releaseOrderRef: string
  releaseDate: string
  releaseSession: SessionType
}

/** joiningDate/session/dbTimestamp are never sent - the server evaluates them from the database clock. */
export interface JoiningReportRequest {
  joiningReportNo: string
  latitude?: number | null
  longitude?: number | null
  accuracyMeters?: number | null
  remarks?: string | null
}

export interface JoiningDecisionRequest {
  approve: boolean
  remarks?: string | null
}

export interface PayrollMovementInputResponse {
  id: number
  movementId: number
  employeeId: number
  employeeName: string
  employeeCode: string
  payMonth: number
  payYear: number
  releasingOfficeName: string | null
  releasingOfficeDays: number
  receivingOfficeName: string | null
  receivingOfficeDays: number
  revisedBasicPay: number | null
  revisedHraTier: 'X' | 'Y' | 'Z' | null
  transitJtDays: number
  transitLwpDays: number
  payrollApplied: boolean
  lpcNumber: string | null
  payrollSyncStatus: PayrollSyncStatus
}

// --- Separated Staff (GET /v1/employees/separated) ---
export interface SeparatedEmployeeResponse {
  id: number
  employeeCode: string
  cpfAcNo: string | null
  fullName: string
  separationType: string | null
  separationDate: string | null
  lastDesignation: string | null
  lastDepartment: string | null
  lastRo: string | null
  lastBasicPay: number | null
  lastScaleGrade: string | null
  pensionSettlementStatus: string | null
  clearanceStatus: string | null
  settlementStatus: string | null
}

// --- Exit Formalities / Clearance (ExitClearanceController) ---
export type SeparationType = 'SUPERANNUATION' | 'RESIGNATION' | 'VRS' | 'DECEASED' | 'TERMINATED'
export type ExitClearanceStatus = 'INITIATED' | 'CLEARANCE_IN_PROGRESS' | 'CLEARANCES_COMPLETED' | 'RELEASE_ORDER_ISSUED' | 'CANCELLED'
export type ExitClearanceDepartment = 'ESTABLISHMENT' | 'VIGILANCE' | 'ESTATE' | 'IT' | 'FINANCE' | 'STORES' | 'CPF_TRUST'
export type ExitClearanceItemStatus = 'PENDING' | 'CLEARED' | 'REJECTED_WITH_DUES'

export interface ExitClearanceItemResponse {
  id: number
  departmentCode: ExitClearanceDepartment
  status: ExitClearanceItemStatus
  duesRecoveryAmount: number | null
  remarks: string | null
  clearedByUserId: number | null
  clearedAt: string | null
}

export interface ExitClearanceRequestResponse {
  id: number
  employeeId: number
  employeeCode: string
  employeeFullName: string
  separationType: SeparationType
  initiatedDate: string
  targetReleaseDate: string
  status: ExitClearanceStatus
  releaseOrderRefNo: string | null
  releaseOrderDate: string | null
  remarks: string | null
  items: ExitClearanceItemResponse[]
}

export interface ExitClearanceInitiateRequest {
  employeeId: number
  separationType: SeparationType
  targetReleaseDate: string
  remarks?: string | null
}

export interface ExitClearanceItemUpdateRequest {
  status: ExitClearanceItemStatus
  duesRecoveryAmount?: number | null
  remarks?: string | null
}

export interface ExitClearanceFinalizeRequest {
  releaseOrderRefNo: string
  releaseOrderDate: string
}

// --- Terminal Settlement (TerminalSettlementController) ---
export type TerminalSettlementStatus = 'DRAFT' | 'AUDITED' | 'APPROVED' | 'DISBURSED'
export type BeneficiaryType = 'SELF' | 'NOMINEE' | 'LEGAL_HEIR'

export interface TerminalSettlementBeneficiaryResponse {
  id: number
  beneficiaryType: BeneficiaryType
  beneficiaryName: string
  relationship: string
  sharePercentage: number
  allocatedAmount: number
  bankAccountNo: string
  bankIfsc: string
  bankName: string | null
  panNumber: string | null
}

export interface TerminalSettlementBeneficiaryRequest {
  beneficiaryType: BeneficiaryType
  beneficiaryName: string
  relationship: string
  sharePercentage: number
  allocatedAmount: number
  bankAccountNo: string
  bankIfsc: string
  bankName?: string | null
  panNumber?: string | null
}

/** id/status are null for a not-yet-saved preview (GET /preview/:employeeId). */
export interface TerminalSettlementResponse {
  id: number | null
  employeeId: number
  separationType: SeparationType
  separationDate: string
  lastBasicPay: number
  daRatePercentage: number
  daAmount: number
  monthlyEmoluments: number
  qualifyingServiceYears: number
  qualifyingServiceMonths: number
  roundedQualifyingYears: number
  elBalanceAtRetirement: number
  hplBalanceAtRetirement: number
  elDaysEncashed: number
  hplDaysEncashed: number
  leaveEncashmentElAmount: number
  leaveEncashmentHplAmount: number
  totalLeaveEncashment: number
  gratuityAmount: number
  isDeathGratuity: boolean
  cpfEmployeeBalance: number
  cpfEmployerBalance: number
  cpfVpfBalance: number
  cpfAccruedInterest: number
  totalCpfPayable: number
  grossTerminalDues: number
  totalRecoveriesDeductions: number
  netTerminalPayable: number
  status: TerminalSettlementStatus | null
  beneficiaries: TerminalSettlementBeneficiaryResponse[]
}

export interface TerminalSettlementGenerateRequest {
  separationType: SeparationType
  separationDate: string
  clearanceRequestId?: number | null
  cpfAccruedInterest?: number | null
}
