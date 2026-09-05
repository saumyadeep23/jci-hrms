import type {
  BloodGroup,
  CourseType,
  DivisionClass,
  DocumentCategory,
  EmploymentCategory,
  Gender,
  IncrementCycle,
  MaritalStatus,
  OnboardingPersonalDetailsRequest,
  PastServiceOrganizationType,
  PastServicePayScalePattern,
  QualificationLevel,
  RecruitmentMode,
  Salutation,
  SalaryBreakdownHeadType,
  SocialCategory,
} from '../../../types/api'

// Steps 1-3's request DTOs are already all-string shapes, so the wizard's
// local form state reuses them directly. Steps 4-7 carry numeric fields
// that need a text <input>, so those get their own string-friendly local
// shape, converted to the real request type only when saving/finalizing.

export const EMPTY_PERSONAL: OnboardingPersonalDetailsRequest = {
  salutation: 'MR' as Salutation,
  firstName: '',
  middleName: '',
  lastName: '',
  gender: 'MALE' as Gender,
  dateOfBirth: '',
  maritalStatus: 'SINGLE' as MaritalStatus,
  bloodGroup: null as BloodGroup | null,
  nationality: 'Indian',
  motherTongue: '',
  panNumber: '',
  cpfAcNo: '',
  aadhaarNumber: '',
  personalEmail: '',
  officialEmail: '',
  phone: '',
  officialMobile: '',
}

export interface LocalSocialProfile {
  socialCategory: SocialCategory
  subCasteCommunity: string
  isPwbd: boolean
  disabilityType: string
  disabilityPercentage: string
  isExServiceman: boolean
  isSportsQuota: boolean
}

export const EMPTY_SOCIAL_PROFILE: LocalSocialProfile = {
  socialCategory: 'GEN',
  subCasteCommunity: '',
  isPwbd: false,
  disabilityType: '',
  disabilityPercentage: '',
  isExServiceman: false,
  isSportsQuota: false,
}

export interface LocalAddress {
  addressType: 'PRESENT' | 'PERMANENT'
  addressLine1: string
  addressLine2: string
  postOffice: string
  policeStation: string
  city: string
  district: string
  state: string
  pinCode: string
}

export function emptyAddress(type: 'PRESENT' | 'PERMANENT'): LocalAddress {
  return { addressType: type, addressLine1: '', addressLine2: '', postOffice: '', policeStation: '', city: '', district: '', state: '', pinCode: '' }
}

export interface LocalBanking {
  bankName: string
  bankBranch: string
  bankAccountNumber: string
  reenterBankAccountNumber: string
  bankIfsc: string
  cancelledChequeS3Key: string
}

export const EMPTY_BANKING: LocalBanking = {
  bankName: '',
  bankBranch: '',
  bankAccountNumber: '',
  reenterBankAccountNumber: '',
  bankIfsc: '',
  cancelledChequeS3Key: '',
}

export interface LocalQualification {
  key: string
  qualificationLevel: QualificationLevel
  degreeTitle: string
  specialization: string
  boardUniversity: string
  institutionName: string
  passingYear: string
  percentageCgpa: string
  divisionClass: DivisionClass | ''
  courseType: CourseType
  highestQualification: boolean
  certificateDocumentS3Key: string
}

export function emptyQualification(): LocalQualification {
  return {
    key: crypto.randomUUID(),
    qualificationLevel: 'GRADUATION',
    degreeTitle: '',
    specialization: '',
    boardUniversity: '',
    institutionName: '',
    passingYear: '',
    percentageCgpa: '',
    divisionClass: '',
    courseType: 'FULL_TIME',
    highestQualification: false,
    certificateDocumentS3Key: '',
  }
}

export interface LocalPastService {
  key: string
  organizationName: string
  organizationType: PastServiceOrganizationType
  designationHeld: string
  fromDate: string
  toDate: string
  lastPayScalePattern: PastServicePayScalePattern | ''
  lastDrawnBasic: string
  lastDrawnGross: string
  qualifyingForPensionGratuity: boolean
  qualifyingServiceOrderRef: string
  reasonForLeaving: string
  experienceCertificateS3Key: string
  relievingNocDocumentS3Key: string
}

export function emptyPastService(): LocalPastService {
  return {
    key: crypto.randomUUID(),
    organizationName: '',
    organizationType: 'PRIVATE_SECTOR',
    designationHeld: '',
    fromDate: '',
    toDate: '',
    lastPayScalePattern: '',
    lastDrawnBasic: '',
    lastDrawnGross: '',
    qualifyingForPensionGratuity: false,
    qualifyingServiceOrderRef: '',
    reasonForLeaving: '',
    experienceCertificateS3Key: '',
    relievingNocDocumentS3Key: '',
  }
}

export interface LocalCtcBreakdownItem {
  key: string
  headCode: string
  headName: string
  headType: SalaryBreakdownHeadType
  amount: string
}

export function emptyCtcItem(): LocalCtcBreakdownItem {
  return { key: crypto.randomUUID(), headCode: '', headName: '', headType: 'EARNING', amount: '' }
}

export interface LocalEmployment {
  employmentCategory: EmploymentCategory
  departmentId: string
  designationId: string
  postId: string
  regularBasicPay: string
  /** Additive grade-scale link (grade_scale_master, V48/V49) - REGULAR/CONTRACTUAL/OUTSOURCED only, drives the Basic Pay/lumpsum/CTC pre-fill in Step6Employment. */
  scaleCode: string
  /** REGULAR only. */
  incrementCycle: IncrementCycle
  /** Pension & Retirement Schemes (V55) - a REGULAR employee defaults to NPS. */
  isNpsEligible: boolean
  isEpsEligible: boolean
  /** Only ever true when isEpsEligible is also true - see EditEmployeeModal's toggle logic, mirrored server-side. */
  isEpsHigherPensionEligible: boolean
  pranNumber: string
  dailyWageRate: string
  wageRevisionOrderNo: string
  fixedLumpSumMonthly: string
  contractStartDate: string
  contractEndDate: string
  contractRefOrder: string
  vendorId: string
  monthlyCtc: string
  billingRateMonthly: string
  agencyEmployeeId: string
  ctcBreakdown: LocalCtcBreakdownItem[]
  dateOfJoiningPsu: string
  recruitmentYear: string
  recruitmentMode: RecruitmentMode
  selectionMethod: string
  recruitmentAgency: string
  advertisementNo: string
  appointmentLetterNo: string
  appointmentLetterDate: string
  offerLetterDate: string
  joiningLetterDate: string
}

export const EMPTY_EMPLOYMENT: LocalEmployment = {
  employmentCategory: 'REGULAR',
  departmentId: '',
  designationId: '',
  postId: '',
  regularBasicPay: '',
  scaleCode: '',
  incrementCycle: 'JULY',
  isNpsEligible: true,
  isEpsEligible: false,
  isEpsHigherPensionEligible: false,
  pranNumber: '',
  dailyWageRate: '',
  wageRevisionOrderNo: '',
  fixedLumpSumMonthly: '',
  contractStartDate: '',
  contractEndDate: '',
  contractRefOrder: '',
  vendorId: '',
  monthlyCtc: '',
  billingRateMonthly: '',
  agencyEmployeeId: '',
  ctcBreakdown: [],
  dateOfJoiningPsu: '',
  recruitmentYear: '',
  recruitmentMode: 'DIRECT_RECRUITMENT',
  selectionMethod: '',
  recruitmentAgency: '',
  advertisementNo: '',
  appointmentLetterNo: '',
  appointmentLetterDate: '',
  offerLetterDate: '',
  joiningLetterDate: '',
}

export interface LocalDependent {
  key: string
  name: string
  relationship: string
  dateOfBirth: string
  isDependent: boolean
  isCoveredMedical: boolean
}

export function emptyDependent(): LocalDependent {
  return { key: crypto.randomUUID(), name: '', relationship: '', dateOfBirth: '', isDependent: true, isCoveredMedical: false }
}

export interface LocalNominee {
  key: string
  name: string
  relationship: string
  sharePercentage: string
  nomineeFor: string
}

export function emptyNominee(): LocalNominee {
  return { key: crypto.randomUUID(), name: '', relationship: '', sharePercentage: '', nomineeFor: 'PF' }
}

export interface LocalFamily {
  fatherName: string
  motherName: string
  spouseName: string
  spouseDob: string
  dependents: LocalDependent[]
  nominees: LocalNominee[]
}

export const EMPTY_FAMILY: LocalFamily = {
  fatherName: '',
  motherName: '',
  spouseName: '',
  spouseDob: '',
  dependents: [],
  nominees: [],
}

export interface LocalDocument {
  key: string
  documentCategory: DocumentCategory
  documentTitle: string
  fileS3Key: string
  mimeType: string
  originalFileName: string
}
