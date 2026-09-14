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
  /** EPFO Universal Account Number - portable across employers, distinct from cpfAcNo. */
  uanNo: string | null
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
  /** EPFO Universal Account Number (12 digits) - portable across employers, distinct from cpfAcNo. Optional. */
  uanNo?: string | null
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
  uanNo?: string | null
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
export type FamilyRelationshipType = 'FATHER' | 'MOTHER' | 'SPOUSE' | 'SON' | 'DAUGHTER'
export type NominationType = 'PF' | 'GRATUITY'
export type CeaEligibilityStatus = 'ELIGIBLE_STANDARD' | 'ELIGIBLE_DIVYANG' | 'INELIGIBLE_OVERAGE'

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
  relationship: FamilyRelationshipType
  dateOfBirth?: string | null
  isDependent: boolean
  isCoveredMedical: boolean
  gender?: Gender | null
  isDivyang: boolean
  disabilityPercentage?: number | null
  isMultipleBirthSecondDelivery: boolean
}

export interface DependentResponse extends DependentRequest {
  id: number
  /** Computed server-side; null unless relationship is SON/DAUGHTER. */
  ceaEligibility: CeaEligibilityStatus | null
  createdAt: string
  updatedAt: string
}

export interface NomineeRequest {
  employeeId: number
  name: string
  relationship: FamilyRelationshipType
  sharePercentage: number
  nomineeFor: NominationType
  /** When set, name/relationship above are ignored server-side and derived from this Family Register row instead. */
  dependentId?: number | null
}

export interface NomineeResponse extends NomineeRequest {
  id: number
  dependentDateOfBirth?: string | null
  createdAt: string
  updatedAt: string
}

// --- Family & Nominees edit tab composite (EmployeeFamilyNomineeCompositeController) - the single
// "Save Changes" payload replacing the old separate family/dependent/nominee save actions. ---
export interface CompositeDependentEntry {
  /** Stable per-row key: the dependent's own id as a string for an existing row, or a UI-generated temp key (e.g. "new-1") for a row added this session - CompositeNomineeEntry.dependentClientKey references this. */
  clientKey: string
  id?: number | null
  name: string
  relationship: FamilyRelationshipType
  dateOfBirth: string
  gender?: Gender | null
  isDependent: boolean
  isCoveredMedical: boolean
  isDivyang: boolean
  disabilityPercentage?: number | null
  isMultipleBirthSecondDelivery: boolean
}

export interface CompositeNomineeEntry {
  id?: number | null
  dependentClientKey: string
  sharePercentage: number
}

/** fatherName/motherName/spouseName/spouseDob are no longer sent directly - the backend derives employee_family_details' own columns from whichever FATHER/MOTHER/SPOUSE row is present in `dependents` (the top-of-tab inputs for them were redundant with the register and have been removed). */
export interface EmployeeFamilyNomineeCompositeRequest {
  dependents: CompositeDependentEntry[]
  pfNominees: CompositeNomineeEntry[]
  gratuityNominees: CompositeNomineeEntry[]
}

export interface EmployeeFamilyNomineeCompositeResponse {
  family: FamilyDetailsResponse | null
  dependents: DependentResponse[]
  pfNominees: NomineeResponse[]
  gratuityNominees: NomineeResponse[]
}

// --- CEA / Hostel Subsidy claims (CeaClaimController) ---
export type CeaClaimType = 'CEA' | 'HOSTEL_SUBSIDY'
export type CeaClaimStatus = 'SUBMITTED' | 'VERIFIED' | 'BILL_PASSED' | 'DISBURSED' | 'REJECTED'

/** claimNo is client-supplied (office-assigned convention, same as MovementOrderCreateRequest.orderRefNo). */
export interface CeaClaimSubmitRequest {
  claimNo: string
  dependentId: number
  academicYear: string
  claimType: CeaClaimType
  schoolName: string
  schoolRegNo?: string | null
  standardClass: string
  periodFrom: string
  periodTo: string
  claimedAmount: number
  supportingDocRef?: string | null
}

export interface CeaClaimResponse {
  id: number
  claimNo: string
  employeeId: number
  employeeCode: string
  fullName: string
  dependentId: number
  dependentName: string
  academicYear: string
  claimType: CeaClaimType
  schoolName: string
  schoolRegNo: string | null
  standardClass: string
  periodFrom: string
  periodTo: string
  claimedAmount: number
  admissibleAmount: number
  passedAmount: number | null
  claimStatus: CeaClaimStatus
  verifiedByOfficerId: number | null
  verifiedAt: string | null
  sanctionOrderNo: string | null
  sanctionDate: string | null
  sanctionedByOfficerId: number | null
  billNo: string | null
  billDate: string | null
  passedByOfficerId: number | null
  passedAt: string | null
  isPayrollProcessed: boolean
  payrollBatchId: number | null
  supportingDocRef: string | null
  rejectionReason: string | null
  createdAt: string
  updatedAt: string
}

/** adjustedAdmissibleAmount is optional - null keeps the amount computed at submission time. */
export interface CeaClaimVerifyRequest {
  adjustedAdmissibleAmount?: number | null
}

export interface CeaClaimBillPassRequest {
  passedAmount: number
  billNo: string
  billDate: string
  sanctionOrderNo: string
  sanctionDate: string
}

export interface CeaClaimRejectRequest {
  reason: string
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
  | 'CPF_DISPUTE_ATTACHMENT'
  | 'CPF_WITHDRAWAL_SUPPORTING_DOC'
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
  isRemoteArea: boolean
  /** "0.00" when isRemoteArea is false; (0.00, 100.00] when true - see V67's chk_state_remote_allowance_rule. */
  remoteAllowancePercentage: number
  createdAt: string
  updatedAt: string
}

export interface StateMasterRequest {
  stateCode: string
  stateName: string
  stateType: StateType
  active: boolean
  isRemoteArea: boolean
  remoteAllowancePercentage: number
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
  /** EL encashment structured metadata (V64) - null for every non-encashment event type. */
  daysEncashed: number | null
  daRate: number | null
  grossAmount: number | null
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
  gradeScaleId?: number | null
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

/** Tracked separately from `status` - a PENDING_APPROVAL application can pass through zero or more RECOMMENDED forwards before reaching SANCTIONED/REJECTED. */
export type LeaveWorkflowStage = 'SUBMITTED' | 'RECOMMENDED' | 'SANCTIONED' | 'REJECTED' | 'CANCELLED'

export type LeaveActionType = 'SUBMIT' | 'RECOMMEND_FORWARD' | 'SANCTION' | 'REJECT'

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
  workflowStage: LeaveWorkflowStage
  /** Whose desk the file is on right now - starts as approverEmployeeId at submit(), moves with every forward(). */
  currentAssignedToEmployeeId: number | null
  currentAssignedToName: string | null
  currentAssignedToDesignation: string | null
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

// --- Leave routing / forwarding / sanctioning (LeaveRoutingController, /api/v1/leaves) ---
export interface LeaveForwardRequest {
  forwardedToEmployeeId: number
  remarks?: string | null
}

/** Shared by sanction (remarks optional) and reject (remarks mandatory - the backend enforces that). */
export interface LeaveDecisionRequest {
  remarks?: string | null
}

export interface LeaveRoutingActionResponse {
  id: number
  actionType: LeaveActionType
  actionByEmployeeId: number
  actionByName: string
  actionByDesignation: string | null
  forwardedToEmployeeId: number | null
  forwardedToName: string | null
  remarks: string | null
  createdAt: string
}

export interface LeaveSanctionHistoryResponse {
  id: number
  employeeCode: string
  employeeName: string
  leaveTypeCode: string
  startDate: string
  endDate: string
  totalDays: number
  forwardedByName: string | null
  sanctionedByName: string | null
  decidedAt: string
  status: string
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
  | 'TRANSFER_JT_CONVERSION'
  | 'TERMINAL_ENCASHMENT'

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
  fullName: string
  designation: string | null
  /** Null when the caller isn't the admin-review listing (the only endpoint that resolves it - see LeaveEncashmentService.listForAdminReview). */
  currentBasicPay: number | null
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
  /** Null for applications submitted before V63 and never finance-approved since. */
  daRateApplied: number | null
  daEffectiveDate: string | null
  grossAmount: number | null
  arrearSettled: boolean
  arrearAmount: number
  arrearDaRateDiff: number
  /** Non-null once queued into a payroll run (PayrollRunService.compute()); null means not yet queued for any run. */
  payrollCycleYear: number | null
  payrollCycleMonth: number | null
  payrollProcessed: boolean
  /** The arrear's own (possibly later) payroll queue - independent of payrollCycleYear/Month above. */
  arrearPayrollCycleYear: number | null
  arrearPayrollCycleMonth: number | null
  applicationDate: string
  createdAt: string
  updatedAt: string
}

/** Month/year-wise sanction history row - voucherRefNo is a synthesized "ELE-{id}" display value, not a real finance voucher number (this schema has no separate voucher-numbering system). */
export interface LeaveEncashmentHistoryResponse {
  id: number
  voucherRefNo: string
  employeeId: number
  employeeCode: string
  fullName: string
  designation: string | null
  elDaysClaimed: number
  basicPay: number | null
  daRateApplied: number | null
  grossAmount: number | null
  arrearAmount: number
  arrearSettled: boolean
  hrApprovedAt: string | null
  hrApprovedByName: string | null
  financeApprovedAt: string | null
  financeApprovedByName: string | null
  status: 'SANCTIONED' | 'REJECTED'
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
  employeeName: string
  attendanceDate: string
  dailyAttendanceId: number | null
  reasonCode: RegularizationReasonCode
  remarks: string | null
  correctedInTime: string
  correctedOutTime: string
  /** The day's real punch times (DailyAttendance.inTime/outTime) - null when there is no linked daily_attendance row (e.g. a genuinely missed punch being regularized) or no punch was recorded on that side. */
  actualInTime: string | null
  actualOutTime: string | null
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

// --- CPSE IDA Enhancement / Arrear Projection (IdaProjectionController) ---
export type DaProjectionBatchStatus = 'DRAFT' | 'ORDER_COMMITTED'

export interface IdaProjectionSimulateRequest {
  scaleType: ScaleType
  newDaRate: number
  effectiveFrom: string
  drawalMonth: number
  drawalYear: number
}

/** One calendar month's org-wide totals across every impacted employee. */
export interface IdaProjectionMonthWiseSummary {
  salMonth: number
  salYear: number
  monthLabel: string
  totalDeltaDa: number
  totalEmployeeCpfArrear: number
  totalEmployerJcpfArrear: number
  totalEmployeeNpsArrear: number
  totalEmployerNpsArrear: number
  totalNetMonthlyArrear: number
  totalEmployerCostMonthly: number
}

export interface IdaProjectionSummaryResponse {
  id: number
  projectionCode: string
  scaleType: ScaleType
  oldDaRate: number
  newDaRate: number
  effectiveFrom: string
  expectedDrawalMonth: number
  expectedDrawalYear: number
  retroMonthsCount: number
  totalActiveEmployees: number
  totalMonthlyGrossDelta: number
  totalMonthlyEmployerCostDelta: number
  totalArrearGrossOutgo: number
  totalArrearNetOutgo: number
  totalEmployerCostOutgo: number
  status: DaProjectionBatchStatus
  monthWiseBreakup: IdaProjectionMonthWiseSummary[]
}

export interface IdaProjectionMonthlyBreakupResponse {
  salMonth: number
  salYear: number
  monthLabel: string
  totalDays: number
  paidDays: number
  actualBasicPay: number
  oldDaRate: number
  newDaRate: number
  deltaDa: number
  employeeCpfArrear: number
  employerJcpfArrear: number
  employeeNpsArrear: number
  employerNpsArrear: number
  netMonthlyArrear: number
  employerCostMonthly: number
}

export type PensionScheme = 'CPF' | 'NPS'

export interface IdaProjectionEmployeeResponse {
  employeeId: number
  employeeCode: string
  fullName: string
  designation: string | null
  pensionScheme: PensionScheme
  totalMonthsCount: number
  totalGrossArrears: number
  totalEmployeeCpfArrear: number
  totalEmployerJcpfArrear: number
  totalEmployeeNpsArrear: number
  totalEmployerNpsArrear: number
  totalLeaveEncashmentArrear: number
  totalNetArrearPayable: number
  totalEmployerCost: number
  monthlyBreakups: IdaProjectionMonthlyBreakupResponse[]
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

// --- Payroll Batches (unified Payroll Computation Engine - PayrollBatchController) ---

export type PayrollBatchStatus = 'DRAFT' | 'CALCULATED' | 'HR_FINALIZED' | 'FINANCE_APPROVED' | 'REJECTED_TO_HR' | 'DISBURSED' | 'CANCELLED'
export type PayrollBatchType = 'REGULAR' | 'SUPPLEMENTARY'

export interface PayrollBatchResponse {
  id: number
  batchNo: string
  salMonth: number
  salYear: number
  financialYear: string
  status: PayrollBatchStatus
  batchType: PayrollBatchType
  payDate: string | null
  totalEmployees: number
  totalGross: number
  totalDeductions: number
  totalNet: number
}

export interface PayrollHeadLineResponse {
  headCount: number
  shortName: string
  description: string
  category: 'EARNING' | 'DEDUCTION' | 'STATUTORY'
  amount: number
}

export interface PayrollMonthlyHeadItemResponse {
  headCount: number
  amount: number
}

export interface PayrollMonthlyRecordResponse {
  tranId: number
  empCode: string
  employeeName: string
  month: number
  year: number
  basicPay: number
  grossAmount: number
  totalDeductions: number
  netAmount: number
  salaryHeld: boolean
  leaveEncashmentAmount: number
  tdsAmount: number
  tdsOverridden: boolean
  headItems: PayrollMonthlyHeadItemResponse[]
  fullHeadLines: PayrollHeadLineResponse[]
}

export interface PayrollBatchDetailsResponse {
  batch: PayrollBatchResponse
  records: PayrollMonthlyRecordResponse[]
}

export interface EditPayrollLineDto {
  headCount: number
  newAmount: number
  changeReason: string
}

export interface PayrollEditResponse {
  tranId: number
  changedHeads: PayrollHeadLineResponse[]
  grossAmount: number
  totalDeductions: number
  netAmount: number
}

export type PayrollReportType =
  | 'SUMMARY_SHEET'
  | 'CPF_SCHEDULE'
  | 'INCOME_TAX_SCHEDULE'
  | 'NPS_SCHEDULE'
  | 'PTAX_SCHEDULE'
  | 'COOPERATIVE_SCHEDULE'
  | 'RECREATION_CLUB_SCHEDULE'

export interface TabularReportResponse {
  columns: string[]
  rows: Record<string, unknown>[]
  totalElements: number
}

// --- ESS Salary Slips ---

export interface EssSalarySlipSummaryResponse {
  tranId: number
  month: number
  year: number
  batchNo: string
  grossAmount: number
  totalDeductions: number
  netAmount: number
  salaryHeld: boolean
}

export interface EssSalarySlipDetailResponse {
  tranId: number
  empCode: string
  employeeName: string
  designation: string | null
  month: number
  year: number
  grossAmount: number
  totalDeductions: number
  netAmount: number
  headLines: PayrollHeadLineResponse[]
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

// --- JCI Payroll Engine master configuration (PayrollMasterController, /api/v1/payroll/masters) ---
// CityClass ('X'|'Y'|'Z') is already declared above (Regional Office section) - reused here as-is.

/** No designationId - the live payroll_transport_allowance_rates table keys a rate by grade scale + city class only. */
export interface TransportAllowanceResponse {
  id: number
  gradeScaleId: number | null
  scaleCode: string | null
  cityClass: CityClass
  baseRate: number
  effectiveFrom: string
  createdAt: string
}

export interface TransportAllowanceRequest {
  gradeScaleId: number
  cityClass: CityClass
  baseRate: number
  effectiveFrom: string
}

export interface ProcurementAllowanceResponse {
  id: number
  designationId: number
  designationTitle: string
  monthlyAllowance: number
  effectiveFrom: string
  effectiveTo: string | null
  createdAt: string
}

export interface ProcurementAllowanceRequest {
  designationId: number
  monthlyAllowance: number
  effectiveFrom: string
}

export interface PtaxSlabResponse {
  id: number
  stateCode: string
  slabMin: number
  slabMax: number | null
  taxAmount: number
  specialMonth: number | null
  specialMonthTax: number | null
  effectiveFrom: string
  effectiveTo: string | null
  createdAt: string
}

export interface PtaxSlabRequest {
  stateCode: string
  slabMin: number
  slabMax: number | null
  taxAmount: number
  specialMonth: number | null
  specialMonthTax: number | null
  effectiveFrom: string
}

export type SalaryHeadEffectType = 'EARNING' | 'DEDUCTION' | 'NO_EFFECT'

/** headCount is the catalog's own primary key (not a display-order field) - see SalaryHead.java's javadoc. */
export interface SalaryHeadResponse {
  headCount: number
  description: string
  shortName: string
  effectType: SalaryHeadEffectType
  isVariable: boolean
  applicableFor: string
  salSlipVis: number | null
  basicDependent: boolean
  refAccountCode: string | null
}

/** PUT /api/v1/payroll/masters/salary-heads/{headCount} - every catalog field except headCount itself is editable. */
export interface SalaryHeadUpdateRequest {
  description: string
  shortName: string
  effectType: SalaryHeadEffectType
  isVariable: boolean
  applicableFor: string
  salSlipVis: number
  basicDependent: boolean
  refAccountCode: string
}

export interface StatutoryHeadResponse {
  statHeadCount: number
  statHeadDescr: string
  statHeadShortName: string
}

/** PUT /api/v1/payroll/masters/statutory-heads/{statHeadCount}. */
export interface StatutoryHeadUpdateRequest {
  description: string
  shortName: string
}

export type NpsDeclarationStatus = 'ACTIVE' | 'SUPERSEDED' | 'CANCELLED'

export interface NpsDeclarationResponse {
  id: number
  employeeId: number
  employeeCode: string
  employeeName: string
  financialYear: string
  npsPercentage: number
  effectiveFrom: string
  status: NpsDeclarationStatus
  remarks: string | null
  createdAt: string
}

/** npsPercentage must be within [3.00, 10.00]. */
export interface NpsDeclarationRequest {
  employeeId: number
  npsPercentage: number
  remarks?: string | null
}

// --- NPS Declaration Desk & HR Compliance Dashboard (NpsDeclarationController, /api/v1/payroll/declarations/nps) ---
export interface NpsPreviewResponse {
  employeeId: number
  employeeCode: string
  employeeName: string
  basicPay: number
  dearnessAllowance: number
  currentFinancialYear: string
  alreadyDeclaredForCurrentFy: boolean
  currentFyDeclaration: NpsDeclarationResponse | null
  history: NpsDeclarationResponse[]
}

export interface NpsSubmittedRow {
  employeeId: number
  employeeCode: string
  employeeName: string
  officeOrDpc: string | null
  declaredPercentage: number
  monthlyDeduction: number | null
  submissionDate: string
  remarks: string | null
}

export interface NpsPendingRow {
  employeeId: number
  employeeCode: string
  employeeName: string
  officeOrDpc: string | null
}

export interface NpsAdminSummaryResponse {
  financialYear: string
  submitted: NpsSubmittedRow[]
  pending: NpsPendingRow[]
}

// --- HRA Rate Master (PayrollMasterController, /api/v1/payroll/masters/hra-rates) ---
export interface PayrollHraRateResponse {
  id: number
  cityClass: CityClass
  ratePercentage: number
  minAmount: number
  effectiveFrom: string
  effectiveTo: string | null
  remarks: string | null
  createdAt: string
  updatedAt: string
}

export interface PayrollHraRateRequest {
  cityClass: CityClass
  ratePercentage: number
  minAmount: number
  effectiveFrom: string
  effectiveTo?: string | null
  remarks?: string | null
}

// --- Employee Company Accommodation / Quarter Allotments (EmployeeQuarterAllotmentController,
// /api/v1/employees/{employeeId}/quarter-allotments) - address-based (JCI leases/provides
// accommodation at an address, it does not own quarters in an estate). ---
export type QuarterAllotmentStatus = 'OCCUPIED' | 'VACATED' | 'SURRENDERED' | 'CANCELLED'

export interface QuarterAllotmentResponse {
  id: number
  employeeId: number
  allotmentOrderNo: string | null
  addressLine1: string
  addressLine2: string | null
  city: string | null
  stateCode: string | null
  pincode: string | null
  syncCurrentAddress: boolean
  licenseFee: number
  waterCharges: number
  electricCharges: number
  allottedFrom: string
  vacatedOn: string | null
  status: QuarterAllotmentStatus
  remarks: string | null
  createdAt: string
  updatedAt: string
}

/** When syncCurrentAddress is true, this address also overwrites the employee's PRESENT address on save. */
export interface QuarterAllotmentRequest {
  allotmentOrderNo?: string | null
  addressLine1: string
  addressLine2?: string | null
  city: string
  stateCode: string
  pincode: string
  syncCurrentAddress: boolean
  licenseFee: number
  waterCharges: number
  electricCharges: number
  allottedFrom: string
  vacatedOn?: string | null
  status: QuarterAllotmentStatus
  remarks?: string | null
}

// --- Vehicle Allotment Transaction Management (EmployeeVehicleAllotmentController,
// /api/v1/employees/{employeeId}/vehicle-allotments) ---
export type VehicleAllotmentStatus = 'ACTIVE' | 'SURRENDERED' | 'TRANSFERRED' | 'CANCELLED'

export interface VehicleAllotmentResponse {
  id: number
  employeeId: number
  allotmentOrderNo: string | null
  vehicleRegNo: string
  vehicleMakeModel: string | null
  driverProvided: boolean
  personalUseAllowed: boolean
  deductionApplicable: boolean
  monthlyDeductionAmount: number
  allottedFrom: string
  surrenderedOn: string | null
  status: VehicleAllotmentStatus
  remarks: string | null
  createdAt: string
  updatedAt: string
}

export interface VehicleAllotmentRequest {
  allotmentOrderNo?: string | null
  vehicleRegNo: string
  vehicleMakeModel?: string | null
  driverProvided: boolean
  personalUseAllowed: boolean
  deductionApplicable: boolean
  monthlyDeductionAmount: number
  allottedFrom: string
  remarks?: string | null
}

export interface VehicleAllotmentSurrenderRequest {
  surrenderedOn: string
  remarks?: string | null
}

// --- Statutory Parameters console (PayrollMasterController, /api/v1/payroll/masters/statutory-parameters) ---
export type StatutoryParamValueType = 'DECIMAL' | 'PERCENTAGE' | 'AMOUNT'

export interface StatutoryParameterResponse {
  id: number
  paramKey: string
  paramName: string
  paramValue: number
  valType: StatutoryParamValueType
  effectiveFrom: string
  effectiveTo: string | null
  remarks: string | null
  updatedAt: string
}

export interface StatutoryParameterReviseRequest {
  newValue: number
  newEffectiveFrom: string
  remarks?: string | null
}

// --- Deputation & Suspension lifecycle / PIMS Reporting Hub "Deputed Staff" & "Suspended Staff" tabs ---
export type DeputationDirection = 'DEPUTATION_OUT' | 'DEPUTATION_IN'
export type DeputationStatus = 'ACTIVE' | 'REPATRIATED'
export type PayOption = 'PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE' | 'FOREIGN_POST_PAY_SCALE'
export type LspcBorneBy = 'BORROWING_ORG' | 'LENDING_ORG'

export interface DeputedStaffReportDto {
  employeeId: number
  empCode: string
  employeeName: string
  cadre: string | null
  designation: string | null
  deputationDirection: DeputationDirection
  organizationName: string
  organizationType: string
  postingStation: string
  isSameStation: boolean
  periodFrom: string
  periodTo: string
  extensionValidUpTo: string | null
  payOption: PayOption
  deputationAllowanceRate: number
  deputationAllowanceCap: number
  lspcApplicable: boolean
  lspcBorneBy: LspcBorneBy
  lspcMonthlyRate: number
  status: DeputationStatus
}

export interface DeputedStaffReportResponse {
  rows: DeputedStaffReportDto[]
  totalDeputedOut: number
  totalDeputedIn: number
  dueForRepatriationThisQuarter: number
}

export type SuspensionStatus = 'UNDER_SUSPENSION' | 'REVOKED'
export type RegularizationType = 'REINSTATED' | 'DISMISSED' | 'COMPULSORILY_RETIRED'
export type NecStatus = 'VERIFIED' | 'PENDING_VERIFICATION' | 'NOT_SUBMITTED'

export interface SuspendedStaffReportDto {
  employeeId: number
  empCode: string
  employeeName: string
  cadre: string | null
  designation: string | null
  hqStation: string
  suspensionOrderNo: string
  suspensionOrderDate: string
  effectiveFrom: string
  daysUnderSuspension: number
  currentSubsistencePercentage: number
  isReviewOverdue: boolean
  currentMonthNecStatus: NecStatus
  status: SuspensionStatus
  regularizationType: RegularizationType | null
}

export interface SuspendedStaffReportResponse {
  rows: SuspendedStaffReportDto[]
  totalUnderSuspension: number
  pendingNecThisMonth: number
  pending90DayReviews: number
}

// --- CPF Trust: Incoming Fund Transfer-In (CpfTrustController) ---
export type IncomingTransferStatus = 'SUBMITTED' | 'VERIFIED_BY_TRUST' | 'CREDITED_TO_LEDGER' | 'REJECTED'
export type IncomingTransferType = 'PF_ONLY' | 'PENSION_ONLY' | 'PF_AND_PENSION'
export type IncomingTransferPaymentMode = 'CHEQUE' | 'DEMAND_DRAFT' | 'NEFT' | 'RTGS' | 'OTHER'
// PastServiceOrganizationType is already declared above (Past Service Records section) - reused here as-is,
// since employee_incoming_fund_transfers.source_organization_type shares the exact same enum.

export interface IncomingFundTransferRequest {
  employeeId: number
  pastServiceRecordId?: number | null
  sourceOrganizationName: string
  sourceOrganizationType: PastServiceOrganizationType
  transferType: IncomingTransferType
  relievingDate: string
  jciJoiningDate: string
  paymentMode: IncomingTransferPaymentMode
  instrumentOrUtrNo: string
  instrumentDate: string
  bankRealizationDate: string
  bankAccountCode: string
  eeCpfPrincipal: number
  eeCpfInterest: number
  erJcpfPrincipal: number
  erJcpfInterest: number
  vpfPrincipal: number
  vpfInterest: number
  totalCpfTransferred: number
  /** Short scheme code (EPS-95, NPS, ...) - max 10 chars. */
  pensionScheme: string
  pensionCorpusAmount?: number | null
  pranOrPpoNo?: string | null
  pastQualifyingServiceYears?: number | null
  pastQualifyingServiceDays?: number | null
  gratuityTransferredAmount?: number | null
  gratuityServiceCounted: boolean
  annexureKDocRef?: string | null
  sanctionOrderNo?: string | null
  sanctionDate?: string | null
  createdByEmployeeId?: number | null
}

export interface IncomingFundTransferResponse {
  id: number
  transferReferenceNo: string
  employeeId: number
  employeeCode: string
  employeeName: string
  pastServiceRecordId: number | null
  sourceOrganizationName: string
  sourceOrganizationType: PastServiceOrganizationType
  transferType: IncomingTransferType
  relievingDate: string
  jciJoiningDate: string
  paymentMode: IncomingTransferPaymentMode
  instrumentOrUtrNo: string
  instrumentDate: string
  bankRealizationDate: string
  bankAccountCode: string
  eeCpfPrincipal: number
  eeCpfInterest: number
  erJcpfPrincipal: number
  erJcpfInterest: number
  vpfPrincipal: number
  vpfInterest: number
  totalCpfTransferred: number
  pensionScheme: string
  pensionCorpusAmount: number
  pranOrPpoNo: string | null
  pastQualifyingServiceYears: number
  pastQualifyingServiceDays: number
  gratuityTransferredAmount: number
  gratuityServiceCounted: boolean
  annexureKDocRef: string | null
  sanctionOrderNo: string | null
  sanctionDate: string | null
  status: IncomingTransferStatus
  creditedAt: string | null
  creditedByEmployeeId: number | null
  rejectionRemarks: string | null
  createdAt: string
}

export interface CreditLedgerRequest {
  trustOfficerId: number
  remarks?: string | null
}

export interface RejectRemarksRequest {
  remarks: string
}

// --- CPF Trust: Member Passbook + Para 60(2) rate resolution + interim settlement (CpfTrustController) ---
export type CpfLedgerEntryType =
  | 'OPENING_BALANCE'
  | 'PAYROLL_MONTHLY'
  | 'DA_ARREAR'
  | 'TRANSFER_IN'
  | 'LOAN_WITHDRAWAL'
  | 'LOAN_REPAYMENT'
  | 'ANNUAL_INTEREST'
  | 'INTERIM_SETTLEMENT_INTEREST'
  | 'FINAL_SETTLEMENT'

/** Mirrors v_member_cpf_passbook (V80) - remarks is deliberately not included, the passbook display never shows it. */
export interface CpfTrustLedgerEntryResponse {
  id: number
  employeeId: number
  finYear: string
  salMonth: number | null
  salYear: number | null
  valueDate: string
  /** "Mon-YYYY", e.g. "Apr-2024" - derived from salMonth/salYear, falling back to valueDate. */
  displayPeriod: string
  entryType: CpfLedgerEntryType
  eeShareCredit: number
  eeShareDebit: number
  erShareCredit: number
  erShareDebit: number
  vpfCredit: number
  vpfDebit: number
  interestCredit: number
  totalCredit: number
  totalDebit: number
  /** Diversion / Loan Sanction this row - Refundable Loan (debits EE) and Non-Refundable Withdrawal (head-wise). */
  sancCpfLoan: number
  sancNrwEe: number
  sancNrwEr: number
  sancNrwVpf: number
  /** Loan Repayment this row - both credited to EE, see CpfLedgerSyncService. */
  loanRepayPrincipal: number
  loanRepayInterest: number
  runningEeBalance: number
  runningErBalance: number
  runningVpfBalance: number
  runningTotalBalance: number
  /** Outstanding Refundable Loan balance after this row. */
  runningLoanCpfBalance: number
  /** Cumulative Non-Refundable Withdrawals taken from each fund to date (never reduced). */
  runningNrwEeBalance: number
  runningNrwErBalance: number
  runningNrwVpfBalance: number
  payrollRunId: number | null
  transferId: number | null
  loanId: number | null
  isProvisionalRate: boolean
  rateApplied: number | null
  rateSourceFinYear: string | null
  referenceDocNo: string | null
  createdAt: string
}

/** GET /api/v1/payroll/trust/cpf/passbook/{employeeId}?finYear= - includes the FY's posted ledger plus, when
 * this FY's interest isn't posted yet, an in-memory-only shadow-accrual projection (accruedInterestFytd). */
export interface CpfPassbookResponse {
  employeeId: number
  finYear: string
  entries: CpfTrustLedgerEntryResponse[]
  ledgerBalance: number
  accruedInterestFytd: number
  effectiveTotalCorpus: number
  /** Current Outstanding Refundable Loan Balance, across all financial years - 0 if no CPF Trust loan has ever been disbursed. */
  outstandingLoanBalance: number
  rateApplied: number
  rateSourceFinYear: string
  isProvisionalRate: boolean
  provisionalNotice: string | null
}

// ================================================================================================
// CPF Passbook V2 - EPS display + Transaction Dispute (Parts 4-16/40 of the module spec).
// GET /api/v1/ess/cpf/passbook/... and /api/v1/ess/cpf/disputes/... - always employeeId-from-JWT,
// never a client-supplied employeeId param. See CpfSelfServicePassbookController/CpfSelfServiceDisputeController.
// ================================================================================================

export interface CpfContributionSummaryResponse {
  employeeContribution: number
  employerContribution: number
  epsContribution: number
  vpfContribution: number
  totalContribution: number
}

/** GET /api/v1/ess/cpf/passbook/summary?finYear= */
export interface CpfPassbookSummaryResponse {
  finYear: string
  auditedBalance: number
  accruedInterest: number
  effectiveCorpus: number
  outstandingLoan: number
  rateApplied: number
  rateSourceFinYear: string
  isProvisionalRate: boolean
  provisionalNotice: string | null
  contributionSummary: CpfContributionSummaryResponse
}

export type CpfDisputeCategory =
  | 'EMPLOYEE_CONTRIBUTION'
  | 'EMPLOYER_CONTRIBUTION'
  | 'EPS_CONTRIBUTION'
  | 'VPF_CONTRIBUTION'
  | 'INTEREST'
  | 'LOAN_SANCTION'
  | 'LOAN_REPAYMENT'
  | 'WITHDRAWAL'
  | 'TRANSACTION_MISSING'
  | 'BALANCE'
  | 'TRANSACTION_DATE'
  | 'OTHER'

/**
 * OPEN -> UNDER_REVIEW -> RESOLVED | REJECTED, OPEN -> UNDER_REVIEW -> CLARIFICATION_REQUIRED -> UNDER_REVIEW
 * -> (RESOLVED|REJECTED), OPEN -> WITHDRAWN. RESOLVED/REJECTED/WITHDRAWN are terminal.
 */
export type CpfDisputeStatus = 'OPEN' | 'UNDER_REVIEW' | 'CLARIFICATION_REQUIRED' | 'RESOLVED' | 'REJECTED' | 'WITHDRAWN'

/** Part 5.2/40 - the small "does this transaction have a dispute" badge, never the full dispute record. */
export interface CpfDisputeBadgeResponse {
  disputeId: number
  disputeNumber: string
  status: CpfDisputeStatus
}

/** Part 5 compact transaction row - GET /api/v1/ess/cpf/passbook/transactions. */
export interface CpfPassbookTransactionSummaryResponse {
  id: number
  transactionDate: string
  displayPeriod: string
  transactionType: CpfLedgerEntryType
  displayAmount: number
  isCredit: boolean
  dispute: CpfDisputeBadgeResponse | null
}

/** Part 5.1/26 full transaction detail - GET /api/v1/ess/cpf/passbook/transactions/{id}. */
export interface CpfPassbookTransactionDetailResponse {
  id: number
  finYear: string
  transactionDate: string
  postingDate: string
  displayPeriod: string
  transactionType: CpfLedgerEntryType
  source: string
  referenceDocNo: string | null
  contribution: {
    employeeCpf: number
    employerCpf: number
    eps: number
    vpf: number
    total: number
  }
  adjustment: {
    employeeDebit: number
    employerDebit: number
    vpfDebit: number
    loanSanctioned: number
    loanPrincipalRepaid: number
    loanInterestRepaid: number
    nonRefundableWithdrawal: number
  }
  balance: {
    employeeBalance: number
    employerBalance: number
    vpfBalance: number
    totalBalance: number
  }
  audit: {
    createdAt: string
    sourceModule: string
  }
  isProvisionalRate: boolean
  rateApplied: number | null
  rateSourceFinYear: string | null
  dispute: CpfDisputeBadgeResponse | null
}

/** POST /api/v1/ess/cpf/disputes - attachmentS3Key/attachmentOriginalFilename come from a prior
 * POST /api/v1/documents/upload (category=CPF_DISPUTE_ATTACHMENT) call, never a raw file upload here. */
export interface CpfDisputeCreateRequest {
  cpfLedgerTransactionId: number
  disputeCategory: CpfDisputeCategory
  employeeRemarks: string
  attachmentS3Key: string | null
  attachmentOriginalFilename: string | null
}

/** Employee-facing dispute view - deliberately omits reviewer-internal fields (Part 40). */
export interface CpfDisputeResponse {
  id: number
  disputeNumber: string
  cpfLedgerTransactionId: number
  disputeCategory: CpfDisputeCategory
  status: CpfDisputeStatus
  employeeRemarks: string
  attachmentOriginalFilename: string | null
  raisedAt: string
  clarificationRequest: string | null
  employeeResponse: string | null
  resolutionRemarks: string | null
  resolvedAt: string | null
  rejectedAt: string | null
  withdrawnAt: string | null
  version: number
}

export interface CpfDisputeClarificationResponseRequest {
  response: string
}

/** One row of "My Disputes" or the admin dispute queue (Part 12). */
export interface CpfDisputeSummaryResponse {
  id: number
  disputeNumber: string
  employeeId: number
  employeeCode: string
  employeeName: string
  cpfLedgerTransactionId: number
  disputeCategory: CpfDisputeCategory
  status: CpfDisputeStatus
  raisedAt: string
  assignedToEmployeeId: number | null
}

/** Full reviewer-facing dispute view (Part 12) - includes reviewer-internal fields CpfDisputeResponse omits. */
export interface CpfDisputeAdminResponse {
  id: number
  disputeNumber: string
  employeeId: number
  employeeCode: string
  employeeName: string
  cpfLedgerTransactionId: number
  disputeCategory: CpfDisputeCategory
  status: CpfDisputeStatus
  employeeRemarks: string
  attachmentOriginalFilename: string | null
  attachmentS3Key: string | null
  raisedByEmployeeId: number
  raisedAt: string
  assignedToEmployeeId: number | null
  assignedAt: string | null
  reviewerRemarks: string | null
  clarificationRequest: string | null
  employeeResponse: string | null
  resolutionRemarks: string | null
  resolvedByEmployeeId: number | null
  resolvedAt: string | null
  rejectedByEmployeeId: number | null
  rejectedAt: string | null
  withdrawnAt: string | null
  version: number
}

export interface CpfDisputeAssignRequest {
  assigneeEmployeeId: number
  expectedVersion: number
}

export interface CpfDisputeStartReviewRequest {
  expectedVersion: number
}

/** POST .../resolve, .../reject, .../request-clarification - remarks required for all three. */
export interface CpfDisputeReviewActionRequest {
  remarks: string
  expectedVersion: number
}

/** One row of a dispute's status-change timeline (Part 41) - built off the existing generic audit_logs table. */
export interface CpfDisputeHistoryEntryResponse {
  action: string
  performedBy: string | null
  timestamp: string
  previousState: string | null
  newState: string | null
}

export type CpfInterimSettlementType = 'SUPERANNUATION' | 'RESIGNATION' | 'DEATH' | 'TRANSFER_OUT'

export interface CrystallizeInterimInterestRequest {
  employeeId: number
  settlementDate: string
  settlementType: CpfInterimSettlementType
}

// --- CPF Rate of Interest Entry (CpfStatutoryInterestRateController) - the notification master
// CpfRateResolutionService's Para 60(2) resolution reads from. ---
export interface CpfStatutoryInterestRateRequest {
  finYear: string
  baseCpfRate: number
  loanMarkupRate: number
  ministryOrderNo: string
  orderDate: string
}

/** effectiveLoanRate/effectiveFrom/effectiveTo are server-computed, never client-supplied - see CpfStatutoryInterestRateRequest. */
export interface CpfStatutoryInterestRateResponse {
  id: number
  finYear: string
  baseCpfRate: number
  loanMarkupRate: number
  effectiveLoanRate: number
  effectiveFrom: string
  effectiveTo: string
  ministryOrderNo: string
  orderDate: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

// --- CPF Trust Members' List (CpfTrustMemberController) - primary identifiers are cpfAcNo and uanNo. ---
export type CpfSettlementStatus = 'NOT_APPLICABLE' | 'PENDING' | 'IN_PROCESS' | 'OVERDUE' | 'SETTLED'

/** One row of the CPF Trust Members' List - GET /v1/payroll/trust/members. cpfBalance/totalPayable are
 * always server-computed (eeBalance+vpfBalance+erBalance, and +accruedInterest for the latter), never
 * independently entered - see CpfTrustMemberDirectoryService.toResponse(). settlementStatus/settlementDueDate/
 * settlementLagLabel/settlementLagDays are likewise always derived at read time (CpfSettlementCalculator),
 * never stored, so they move forward automatically with today's date. */
export interface CpfTrustMemberResponse {
  employeeId: number
  employeeCode: string
  fullName: string
  cpfAcNo: string
  uanNo: string | null
  status: string
  isSeparated: boolean
  separationDate: string | null

  eeBalance: number
  vpfBalance: number
  erBalance: number
  cpfBalance: number
  /** Current-FY interest not yet posted by an annual interest run - 0 once posted (already folded into the balances above), or if no statutory rate is notified for this FY yet. */
  accruedInterest: number
  totalPayable: number

  /** Raw TerminalSettlementStatus (DRAFT/AUDITED/APPROVED/DISBURSED), or null if no terminal settlement exists yet. */
  rawTerminalSettlementStatus: string | null
  settlementStatus: CpfSettlementStatus
  settlementDueDate: string | null
  settlementDate: string | null
  /** "Due in X days" / "Due today" / "X days overdue" / "Settled" / "-". */
  settlementLagLabel: string
  settlementLagDays: number | null
}

/** GET /v1/payroll/trust/members/summary - the KPI strip, always computed against the full membership (ignores the table's current search/filter). */
export interface CpfTrustMemberSummaryResponse {
  totalMembers: number
  activeAccounts: number
  separatedMembers: number
  pendingSettlement: number
  overdueSettlement: number
  uanMissing: number
  totalCpfBalance: number
}

export interface UpdateUanRequest {
  uanNo: string
}

export type CpfEpsEligibilityStatus = 'ELIGIBLE' | 'NOT_ELIGIBLE' | 'REVIEW_REQUIRED'

/** GET /v1/payroll/trust/members/{employeeId}/eps-eligibility - a placeholder-rule eligibility check, not a confirmed legal determination; see the backend service's own javadoc. */
export interface CpfEpsEligibilityResponse {
  employeeId: number
  fullName: string
  cpfAcNo: string
  uanNo: string | null
  dateOfBirth: string | null
  dateOfJoining: string | null
  dateOfSeparation: string | null
  epsMember: boolean
  eligibleServiceLabel: string
  eligibleServiceDays: number
  pensionableServiceLabel: string
  eligibility: CpfEpsEligibilityStatus
  eligibilityBasis: string
}

// --- CPF Trust: Loan/Withdrawal Origination (CpfLoanController) ---
export type CpfLoanType = 'REFUNDABLE_LOAN' | 'NON_REFUNDABLE_WITHDRAWAL'
export type CpfLoanApplicationStatus = 'APPLIED' | 'SANCTIONED' | 'DISBURSED' | 'CLOSED' | 'REJECTED'
/** cpf_loan_applications.recovery_phase - which of the two sequential payroll recovery phases a DISBURSED loan is in. */
export type CpfLoanRecoveryPhase = 'PRINCIPAL' | 'INTEREST' | 'CLOSED'
export const CPF_LOAN_PURPOSES = ['HOUSING', 'MEDICAL', 'MARRIAGE', 'EDUCATION', 'SPECIAL'] as const
export type CpfLoanPurpose = (typeof CPF_LOAN_PURPOSES)[number]

export interface CpfLoanEligibilityResponse {
  employeeId: number
  runningEeBalance: number
  runningVpfBalance: number
  totalEligibleCorpus: number
  maxPermissibleAmount: number
  activeLoanExists: boolean
  outstandingActiveLoanBalance: number
  eligibilityReason: string
}

export interface CpfLoanApplicationRequest {
  employeeId: number
  loanType: CpfLoanType
  purpose: string
  appliedAmount: number
  totalInstallments: number
  reason?: string | null
}

export interface CpfLoanSanctionRequest {
  sanctionedAmount: number
  sanctionOrderNo: string
  sanctionDate: string
  totalInstallments: number
  /** Number of installments the total interest is spread over - separate from totalInstallments (the principal schedule), since Head 30 then Head 31 recovery runs sequentially, not in parallel. */
  interestInstallments: number
  /** Required (and must sum to sanctionedAmount) only for NON_REFUNDABLE_WITHDRAWAL - ignored for REFUNDABLE_LOAN, which always debits EE only. */
  sancNrwEe?: number | null
  sancNrwEr?: number | null
  sancNrwVpf?: number | null
}

export interface CpfLoanApplicationResponse {
  id: number
  loanApplicationNo: string
  employeeId: number
  employeeCode: string
  employeeName: string
  loanType: CpfLoanType
  purpose: string
  applicationReason: string | null
  appliedAmount: number
  sanctionedAmount: number
  sanctionOrderNo: string | null
  sanctionDate: string | null
  sancNrwEe: number
  sancNrwEr: number
  sancNrwVpf: number
  baseCpfRate: number
  interestRate: number
  totalInterestAmount: number
  monthlyRecoveryPrincipal: number
  monthlyRecoveryInterest: number
  totalInstallments: number
  recoveredInstallments: number
  totalInterestInstallments: number
  recoveredInterestInstallments: number
  outstandingBalance: number
  outstandingInterest: number
  recoveryPhase: CpfLoanRecoveryPhase
  isPreclosed: boolean
  preclosedAt: string | null
  status: CpfLoanApplicationStatus
  rejectionRemarks: string | null
  disbursedAt: string | null
  createdAt: string
  /** Part 7/38 - the cpf_application.id this loan was bridged from, when it originated through the rule-engine flow. Null for a loan applied directly through the legacy CpfLoanController. */
  cpfApplicationId: string | null
}

// --- CPF Loan Settlement (direct out-of-payroll cash/instrument settlement - CpfLoanController) ---
export type CpfLoanSettlementMode = 'CASH' | 'CHEQUE' | 'DEMAND_DRAFT' | 'NEFT' | 'RTGS' | 'OTHER'

/** GET /v1/payroll/trust/loans/{id}/settlement-quote - netPayoffAmount is what fully closes the loan today, factoring in the interest rebate for tenure not actually used. */
export interface CpfLoanSettlementQuoteResponse {
  loanId: number
  elapsedMonths: number
  outstandingBalance: number
  outstandingInterest: number
  originalProjectedInterest: number
  recomputedStatutoryInterest: number
  interestRebateAmount: number
  netPayoffAmount: number
}

export interface CpfLoanSettlementRequest {
  principalPaid: number
  interestPaid: number
  settlementType: CpfLoanSettlementMode
  instrumentOrChallanNo: string
  instrumentDate: string
  bankRealizationDate: string
  trustBankAccountCode: string
  challanDocRef?: string | null
  remarks?: string | null
}

export interface CpfLoanSettlementResponse {
  id: number
  receiptVoucherNo: string
  loanId: number
  loanApplicationNo: string
  employeeId: number
  finYear: string
  settlementType: CpfLoanSettlementMode
  instrumentOrChallanNo: string
  instrumentDate: string
  bankRealizationDate: string
  trustBankAccountCode: string
  principalPaid: number
  interestPaid: number
  totalAmountPaid: number
  isEarlyForeclosure: boolean
  elapsedMonths: number
  originalProjectedInterest: number
  recomputedStatutoryInterest: number
  interestRebateAmount: number
  remarks: string | null
  createdAt: string
}

// --- CPF Trust: DB-driven rule-engine withdrawal/loan origination (CpfApplicationController) - apply ->
// sanction -> disburse, against whichever APPROVED, currently-effective rule version is configured for the
// chosen purpose. Deliberately separate from the legacy CpfLoanController flow above (cpf_application, not
// cpf_loan_applications) - a refundable purpose's successful disbursement bridges into exactly one linked
// CpfLoanApplication (see CpfLoanApplicationResponse.cpfApplicationId) so the existing repayment/recovery/
// settlement machinery activates for it automatically. ---

export interface CpfWithdrawalPurposeResponse {
  id: string
  code: string
  name: string
  description: string | null
  typeCode: string
  active: boolean
}

// CpfRuleDocumentConfig (documentName/mandatory/allowedMimeTypes/maxSizeKb) is already declared further
// below (the admin rule-config shape) - reused here for the eligibility response's own requiredDocuments
// list rather than redeclaring it, since a mandatory document's documentName must be echoed back verbatim
// in CpfApplicationDocumentSubmission.documentName for it to be recognized as satisfied.

/** Task 4 - one evaluated ceiling component (CeilingComponentResponse), structured sibling of calculationTrace. */
export interface CpfCeilingComponent {
  componentName: string
  sourceMetric: 'ELIGIBLE_BALANCE' | 'BASIC_PLUS_DA' | 'PROPERTY_COST' | 'OUTSTANDING_LOAN' | 'PAYROLL_DEDUCTION_CAPACITY' | 'FIXED_AMOUNT'
  calculatedValue: number
}

/** Task 4 - one head's configured debit priority + projected debit amount (HeadAllocationResponse). */
export interface CpfHeadAllocation {
  headCode: string
  headName: string
  debitPriority: number
  previewDebitAmount: number
}

/** Task 4 - non-mutating repayment preview (RepaymentPreviewResponse); principal-first, interest only
 * starts at interestPhaseStartInstallment (1-based). */
export interface CpfRepaymentPreview {
  tenureMonths: number
  principalAmount: number
  monthlyPrincipalInstallment: number
  principalInstallmentCount: number
  interestInstallmentCount: number
  interestPhaseStartInstallment: number
  monthlyInterestInstallment: number
  totalInterest: number
  totalRecovery: number
}

export interface CpfApplicationEligibilityResponse {
  purposeCode: string
  ruleVersionId: string
  ruleVersionTag: string
  eligible: boolean
  eligibilityReason: string
  totalEligibleBalance: number
  eligibleAmount: number
  serviceEligible: boolean
  serviceEligibilityReason: string
  frequencyEligible: boolean
  frequencyReason: string
  calculationTrace: string[]
  requiredDocuments: CpfRuleDocumentConfig[]
  ceilingComponents: CpfCeilingComponent[]
  headAllocation: CpfHeadAllocation[]
  repaymentPreview: CpfRepaymentPreview | null
  carriesRepaymentSchedule: boolean
  minTenureMonths: number | null
  maxTenureMonths: number | null
  defaultTenureMonths: number | null
}

/** A document the applicant actually submitted - documentName must match one of requiredDocuments' own names exactly; s3Key/originalFilename come from a prior POST /v1/documents/upload (category=CPF_WITHDRAWAL_SUPPORTING_DOC). */
export interface CpfApplicationDocumentSubmission {
  documentName: string
  s3Key: string
  originalFilename: string
}

export interface CpfApplicationRequest {
  employeeCode: string
  purposeCode: string
  appliedAmount: number
  basicPlusDa?: number | null
  propertyCost?: number | null
  payrollDeductionCapacity?: number | null
  submittedDocuments: CpfApplicationDocumentSubmission[]
  /** Feeds the OUTSTANDING_LOAN ceiling metric - only HOUSING_LOAN_REPAYMENT's rule reads this today. */
  outstandingLoan?: number | null
}

export type CpfApplicationStatus = 'APPLIED' | 'SANCTIONED' | 'DISBURSED' | 'REJECTED' | 'CLOSED'

export interface CpfApplicationResponse {
  id: string
  applicationNumber: string
  employeeCode: string
  purposeCode: string
  ruleVersionId: string
  ruleVersionTag: string
  status: CpfApplicationStatus
  appliedAmount: number
  eligibleAmount: number
  sanctionedAmount: number | null
  tenureMonths: number | null
  calculatedEmi: number | null
  calculationTrace: string
  submittedDocuments: string
  createdAt: string
  sanctionedAt: string | null
  disbursedAt: string | null
  /** The cpf_loan_applications.id this application's disbursement bridged into, when its purpose is refundable. Null for non-refundable withdrawals/final settlements. */
  linkedLoanId: number | null
}

export interface CpfApplicationSanctionRequest {
  sanctionedAmount: number
  tenureMonths?: number | null
  basicPlusDa?: number | null
  propertyCost?: number | null
  payrollDeductionCapacity?: number | null
  outstandingLoan?: number | null
}

// --- CPF Interest Management (CpfInterestRunController) - the admin-controlled Calculate -> Approve/Post
// -> Reverse -> Recalculate workflow for year-end ANNUAL_INTEREST. See CpfInterestRunService's own javadoc. ---
export type CpfInterestRunScope = 'ALL_MEMBERS' | 'SELECTED_MEMBER'
export type CpfInterestRunStatus = 'CALCULATED' | 'POSTED' | 'REVERSED' | 'FAILED'

/** One row of GET /interest/runs/years - the FY dashboard (Part 6/39 of the module spec). */
export interface CpfInterestFinancialYearStatusResponse {
  finYear: string
  configuredRate: number | null
  calculationBasisAvailable: boolean
  fullYearDataAvailable: boolean
  activeRunId: number | null
  status: CpfInterestRunStatus | null
  postingAllowed: boolean
  dependencyBlockedReason: string | null
  membersProcessed: number
  totalInterestPosted: number
  postedByEmployeeId: number | null
  postedAt: string | null
}

export interface CpfInterestCalculateRequest {
  finYear: string
  scope: CpfInterestRunScope
  employeeId: number | null
  interestOrderNo: string
  interestOrderDate: string
}

export interface CpfInterestMemberBreakdown {
  employeeId: number
  employeeCode: string
  employeeName: string
  openingBalance: number
  totalContributions: number
  totalWithdrawals: number
  eeInterest: number
  erInterest: number
  vpfInterest: number
  totalInterest: number
  projectedClosingBalance: number
  dataReviewRequired: boolean
  dataReviewReason: string | null
  legacyAnomalyWarning: boolean
  legacyAnomalyMessage: string | null
}

export interface CpfInterestCalculationPreviewResponse {
  runId: number
  finYear: string
  scope: CpfInterestRunScope
  interestRate: number
  memberCount: number
  totalOpeningBalance: number
  totalContributions: number
  totalWithdrawals: number
  totalEeInterest: number
  totalErInterest: number
  totalVpfInterest: number
  totalStatutoryInterest: number
  dataReviewRequired: boolean
  members: CpfInterestMemberBreakdown[]
}

export interface CpfAnnualInterestRunResponse {
  id: number
  finYear: string
  scope: CpfInterestRunScope
  memberEmployeeId: number | null
  declaredInterestRate: number
  interestOrderNo: string
  interestOrderDate: string
  runDate: string
  totalMembersProcessed: number
  totalInterestCreditedEe: number
  totalInterestCreditedEr: number
  totalInterestCreditedVpf: number
  status: CpfInterestRunStatus
  dataReviewRequired: boolean
  calculatedByEmployeeId: number | null
  calculatedAt: string | null
  postedByEmployeeId: number | null
  postedAt: string | null
  reversedByEmployeeId: number | null
  reversedAt: string | null
  remarks: string | null
}

// --- CPF Trust Loan & Advances rule engine (CpfWithdrawalRuleController/CpfApplicationController) - the
// DRAFT -> PENDING_VERIFICATION -> PENDING_APPROVAL -> APPROVED workflow driving withdrawal eligibility/
// ceiling calculation. Ids are UUID strings (this schema predates this app's usual Long/BIGSERIAL
// convention - see CpfHeadMaster's own backend javadoc). ---
export type CpfRuleStatus = 'DRAFT' | 'PENDING_VERIFICATION' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'SUPERSEDED' | 'EXPIRED' | 'REQUIRES_CONFIRMATION'
export type CpfFrequencyScope = 'SERVICE' | 'FINANCIAL_YEAR' | 'CALENDAR_YEAR' | 'ROLLING_PERIOD' | 'NONE'
export type CpfRepaymentCreditMethod = 'ORIGINAL_DEBIT_HEAD' | 'CONFIGURED_PRIORITY' | 'PROPORTIONAL' | 'SPECIFIC_HEAD' | 'OTHER_TRUST_RULE'
export type CpfCeilingSourceMetric = 'ELIGIBLE_BALANCE' | 'BASIC_PLUS_DA' | 'PROPERTY_COST' | 'OUTSTANDING_LOAN' | 'PAYROLL_DEDUCTION_CAPACITY' | 'FIXED_AMOUNT'
export type CpfCeilingOperator = 'MULTIPLY' | 'PERCENTAGE' | 'FIXED'

export interface CpfWithdrawalTypeResponse {
  id: string
  code: string
  name: string
  refundable: boolean
  settlement: boolean
  active: boolean
}

export interface CpfWithdrawalPurposeResponse {
  id: string
  code: string
  name: string
  description: string | null
  typeCode: string
  active: boolean
}

export interface CpfHeadMasterResponse {
  id: string
  code: string
  name: string
  classification: string
  interestBearing: boolean
  withdrawalAllowed: boolean
  active: boolean
}

export interface CpfRuleHeadConfig {
  headCode: string
  eligible: boolean
  debitPriority: number
  recreditPriority: number
}

export interface CpfRuleCeilingConfig {
  componentName: string
  sourceMetric: CpfCeilingSourceMetric
  operator: CpfCeilingOperator
  factorValue: number
  displayOrder: number
}

export interface CpfRuleDocumentConfig {
  documentName: string
  mandatory: boolean
  allowedMimeTypes?: string | null
  maxSizeKb?: number | null
}

export interface CpfWithdrawalRuleVersionRequest {
  purposeCode: string
  versionTag: string
  effectiveFrom: string
  changeReason: string
  minServiceMonths: number
  includePreviousService: boolean
  allowBreakInService: boolean
  frequencyScope: CpfFrequencyScope
  maxOccurrences: number
  maxActiveConcurrency: number
  balanceRetentionPct: number | null
  repaymentCreditMethod: CpfRepaymentCreditMethod
  minTenureMonths: number | null
  maxTenureMonths: number | null
  defaultTenureMonths: number | null
  interestRateAnnual: number | null
  interestMethod: string | null
  allowPrepayment: boolean | null
  allowConversion: boolean | null
  payrollCapType: string | null
  taxRuleReference: string | null
  taxServiceThresholdMonths: number | null
  workflowDefinitionCode: string | null
  heads: CpfRuleHeadConfig[]
  ceilings: CpfRuleCeilingConfig[]
  documents: CpfRuleDocumentConfig[]
}

export interface CpfWithdrawalRuleVersionResponse {
  id: string
  purposeCode: string
  versionTag: string
  status: CpfRuleStatus
  effectiveFrom: string
  effectiveTo: string | null
  approvalReference: string | null
  changeReason: string
  createdBy: string
  verifiedBy: string | null
  approvedBy: string | null
  approvedAt: string | null
  minServiceMonths: number
  includePreviousService: boolean
  allowBreakInService: boolean
  frequencyScope: CpfFrequencyScope
  maxOccurrences: number
  maxActiveConcurrency: number
  balanceRetentionPct: number
  repaymentCreditMethod: CpfRepaymentCreditMethod
  minTenureMonths: number | null
  maxTenureMonths: number | null
  defaultTenureMonths: number | null
  interestRateAnnual: number | null
  interestMethod: string | null
  payrollCapType: string
  taxRuleReference: string | null
  taxServiceThresholdMonths: number | null
  workflowDefinitionCode: string
}

export interface CpfRuleSimulatorRequest {
  purposeCode: string
  ruleVersionId?: string | null
  employeeCode?: string | null
  headABalance?: number | null
  headBBalance?: number | null
  headCBalance?: number | null
  basicPlusDa?: number | null
  propertyCost?: number | null
  existingPayrollDeductions?: number | null
  requestedAmount?: number | null
  loanTenureMonths?: number | null
}

export interface CpfRuleSimulatorResponse {
  ruleVersionId: string
  ruleVersionTag: string
  ruleStatus: string
  serviceEligible: boolean
  serviceEligibilityReason: string
  frequencyEligible: boolean
  frequencyReason: string
  totalEligibleBalance: number
  finalEligibleAmount: number
  debitAllocationByHead: Record<string, number>
  projectedEmi: number | null
  taxLikely: boolean
  calculationTrace: string[]
}

export interface CpfApplicationEligibilityResponse {
  purposeCode: string
  ruleVersionId: string
  ruleVersionTag: string
  eligible: boolean
  eligibilityReason: string
  totalEligibleBalance: number
  eligibleAmount: number
  serviceEligible: boolean
  serviceEligibilityReason: string
  frequencyEligible: boolean
  frequencyReason: string
  calculationTrace: string[]
}

export interface CpfApplicationResponse {
  id: string
  applicationNumber: string
  employeeCode: string
  purposeCode: string
  ruleVersionId: string
  ruleVersionTag: string
  status: 'APPLIED' | 'SANCTIONED' | 'DISBURSED' | 'REJECTED' | 'CLOSED'
  appliedAmount: number
  eligibleAmount: number
  sanctionedAmount: number | null
  tenureMonths: number | null
  calculatedEmi: number | null
  calculationTrace: string
  createdAt: string
  sanctionedAt: string | null
  disbursedAt: string | null
}

// ---------------------------------------------------------------------------
// JCIECCS (JCI Employees' Co-Operative Credit Society) - backend controllers:
// JciEccsMemberController, JciEccsLoanController, JciEccsPayrollBatchController,
// JciEccsMigrationController. Field names/enums mirror the backend DTOs exactly.
// ---------------------------------------------------------------------------

export type JciEccsLoanProductCode = 'TERM' | 'EMERGENCY'
export type JciEccsMembershipStatus = 'ACTIVE' | 'SUSPENDED' | 'CLOSED'
export type JciEccsLoanStatus = 'PENDING' | 'ACTIVE' | 'RESTRUCTURED' | 'CLOSED' | 'DEFAULTED' | 'WRITTEN_OFF'
export type JciEccsScheduleStatus = 'FUTURE' | 'DUE' | 'PARTIAL' | 'PAID' | 'OVERDUE' | 'RESTRUCTURED' | 'WAIVED'
export type JciEccsDebitStatus = 'PENDING_DEBIT' | 'DEBIT_SUCCESS' | 'DEBIT_PARTIAL' | 'DEBIT_FAILED' | 'REVERSED'
export type JciEccsCollectionBatchStatus = 'LOCKED' | 'PROCESSED' | 'REOPENED'

export interface JciEccsMemberResponse {
  id: number
  employeeId: number
  employeeCode: string | null
  memberName: string | null
  placeOfPosting: string | null
  membershipCode: string
  membershipDate: string
  membershipStatus: JciEccsMembershipStatus
  shareBalance: number
  fundBalance: number
  securityBalance: number
  thriftMonthlyAmount: number
}

/** PUT /api/jcieccs/members/{id}/status - remarks required server-side for SUSPENDED/CLOSED (Bye-laws 15/16). */
export interface JciEccsMemberStatusChangeRequest {
  status: JciEccsMembershipStatus
  effectiveDate: string
  remarks?: string | null
}

export interface JciEccsLoanResponse {
  id: number
  loanIssueId: string
  memberId: number
  productCode: string
  sanctionDate: string
  disbursementDate: string
  disbursementCycleCode: string
  sanctionedAmount: number
  disbursedAmount: number
  tenureMonths: number
  annualInterestRate: number
  monthlyPrincipalInstallment: number
  outstandingPrincipal: number
  status: JciEccsLoanStatus
  parentLoanId: number | null
  restructuringCount: number
  topupCount: number
}

export interface JciEccsLoanScheduleResponse {
  id: number
  installmentNo: number
  cycleCode: string
  openingPrincipal: number
  principalDue: number
  interestDue: number
  totalDue: number | null
  principalPaid: number
  interestPaid: number
  totalPaid: number | null
  principalOutstanding: number
  status: JciEccsScheduleStatus
}

export interface JciEccsLoanCreateRequest {
  employeeCode: string
  productCode: JciEccsLoanProductCode
  sanctionedAmount: number
  tenureMonths?: number | null
  applicationDate: string
  sanctionDate: string
  disbursementDate: string
}

export interface JciEccsRestructureRequest {
  tenureMonths: number
  effectiveDate: string
}

export interface JciEccsTopUpRequest {
  topUpAmount: number
  tenureMonths: number
  effectiveDate: string
}

export interface JciEccsCashRepaymentRequest {
  principalAmount: number
  interestAmount: number
  repaymentDate: string
  referenceId?: string | null
  idempotencyKey: string
}

export interface JciEccsCollectionDetailResponse {
  id: number
  employeeId: number
  memberId: number
  thriftAmount: number
  termLoanId: number | null
  termPrincipal: number
  termInterest: number
  emergencyLoanId: number | null
  emergencyPrincipal: number
  emergencyInterest: number
  totalSnapshotAmount: number | null
  debitStatus: JciEccsDebitStatus
  actualDebitedAmount: number
}

export interface JciEccsCollectionBatchResponse {
  id: number
  payrollRunId: string
  cycleCode: string
  batchStatus: JciEccsCollectionBatchStatus
  lockedAt: string
  debitConfirmedAt: string | null
  totalExpectedAmount: number
  totalDebitedAmount: number
  details: JciEccsCollectionDetailResponse[]
}

export interface JciEccsFinancialPositionResponse {
  member: JciEccsMemberResponse
  activeTermLoan: JciEccsLoanResponse | null
  activeEmergencyLoan: JciEccsLoanResponse | null
}

// --- JCIECCS Lifecycle Engine Phase 2: three-way reconciliation (DEMAND vs ACTUAL RECOVERY vs LEDGER
// POSTING) + admin integrity checks. Never trust the frontend to compute these - always server-derived. ---

export type JciEccsRecoveryComponent = 'THRIFT' | 'TERM_INTEREST' | 'TERM_PRINCIPAL' | 'EMERGENCY_INTEREST' | 'EMERGENCY_PRINCIPAL'
export type JciEccsRecoverySource = 'PAYROLL' | 'CASH' | 'REVERSAL'
export type JciEccsRecoveryStatus = 'PENDING' | 'CONFIRMED' | 'PARTIAL' | 'FAILED' | 'POSTED' | 'REVERSED' | 'RECONCILIATION_REQUIRED'
export type JciEccsReconciliationStatus =
  | 'MATCHED'
  | 'PARTIAL'
  | 'NOT_RECOVERED'
  | 'OVER_RECOVERED'
  | 'POSTING_PENDING'
  | 'POSTING_MISMATCH'
  | 'REVERSED'
  | 'LOCKED_SNAPSHOT_CONFLICT'
  | 'ERROR'
export type JciEccsReconciliationReasonCode =
  | 'PARTIAL_PAYROLL_DEBIT'
  | 'FAILED_PAYROLL_DEBIT'
  | 'OVER_DEBIT'
  | 'POSTING_MISMATCH'
  | 'DUPLICATE_CONFIRMATION'
  | 'CASH_RECOVERY_AFTER_SNAPSHOT_LOCK'
  | 'REVERSAL_AFTER_PAYROLL_POSTING'
  | 'LOAN_BALANCE_MISMATCH'
  | 'SCHEDULE_RECOVERY_MISMATCH'
  | 'UNKNOWN'
export type JciEccsReconciliationResolutionAction =
  | 'ACKNOWLEDGE'
  | 'MARK_RESOLVED'
  | 'REQUEST_PAYROLL_CORRECTION'
  | 'REVERSE_RECOVERY'
  | 'NO_ACTION_REQUIRED'

export interface JciEccsReconciliationResponse {
  id: number
  payrollRunId: string | null
  collectionDetailId: number | null
  membershipCode: string
  memberName: string | null
  employeeId: number
  loanId: number | null
  loanIssueId: string | null
  component: JciEccsRecoveryComponent | null
  expectedAmount: number
  actualAmount: number
  postedAmount: number
  varianceAmount: number
  status: JciEccsReconciliationStatus
  reasonCode: JciEccsReconciliationReasonCode
  detectedAt: string
  resolved: boolean
  resolvedAt: string | null
  resolvedBy: number | null
  resolutionAction: JciEccsReconciliationResolutionAction | null
  resolutionRemarks: string | null
}

export interface JciEccsReconciliationSummaryResponse {
  payrollRunId: string
  totalLines: number
  countsByStatus: Partial<Record<JciEccsReconciliationStatus, number>>
}

export interface JciEccsResolveReconciliationRequest {
  action: JciEccsReconciliationResolutionAction
  remarks: string
}

export interface JciEccsLoanReconciliationResponse {
  loanId: number
  originalPrincipal: number
  postedPrincipalRecovery: number
  postedPrincipalReversals: number
  derivedOutstanding: number
  storedOutstanding: number
  outstandingVariance: number
  schedulePrincipalRecovered: number
  ledgerPrincipalRecovered: number
  scheduleInterestRecovered: number
  ledgerInterestRecovered: number
  status: JciEccsReconciliationStatus
}

export type JciEccsIntegrityCheckSeverity = 'INFO' | 'WARNING' | 'CRITICAL'

export interface JciEccsIntegrityCheckResultResponse {
  checkType: string
  entityType: string
  entityId: number
  severity: JciEccsIntegrityCheckSeverity
  message: string
  expectedValue: string
  actualValue: string
  detectedAt: string
}

/** The priority-cascade recovery/allocation audit trail (spec section 66). */
export interface JciEccsRecoveryAllocationLine {
  sequence: number
  component: JciEccsRecoveryComponent
  expectedAmount: number
  allocatedAmount: number
  loanId: number | null
  loanScheduleId: number | null
}

export interface JciEccsRecoveryResponse {
  id: number
  source: JciEccsRecoverySource
  status: JciEccsRecoveryStatus
  membershipCode: string
  employeeId: number
  loanId: number | null
  loanIssueId: string | null
  grossAmount: number
  createdAt: string
  postedAt: string | null
  reversalOfRecoveryId: number | null
  allocations: JciEccsRecoveryAllocationLine[]
}

// --- JCIECCS Lifecycle Engine Phase 3: no-dues / settlement position, integrated with the existing HR
// exit-clearance workflow (never a duplicate separation engine). ---

export interface JciEccsSettlementResponse {
  id: number
  membershipCode: string
  employeeId: number
  exitClearanceRequestId: number | null
  exitClearanceItemStatus: string | null
  separationType: string | null
  separationDate: string | null
  shareBalance: number
  fundBalance: number
  securityBalance: number
  thriftBalance: number
  termPrincipalOutstanding: number
  termInterestOutstanding: number
  emergencyPrincipalOutstanding: number
  emergencyInterestOutstanding: number
  otherDues: number
  setoffAmount: number
  netLiability: number
  stale: boolean
  calculatedAt: string
  remarks: string | null
}

export interface JciEccsClearNoDuesRequest {
  remarks: string
}

export interface JciEccsStagingMemberRequest {
  employeeCode: string
  membershipCode: string
  membershipDate: string
  shareBalance?: number | null
  fundBalance?: number | null
  securityBalance?: number | null
  thriftMonthlyAmount?: number | null
}

export interface JciEccsMigrationStatusResponse {
  pending: number
  promoted: number
  rejected: number
  rejectedRows: { id: number; employeeCode: string; membershipCode: string; rejectionReason: string | null }[]
}
