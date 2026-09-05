import type {
  EmployeeAddressRequest,
  EmployeeBankAccountRequest,
  OnboardingDependentEntry,
  OnboardingDocumentEntry,
  OnboardingEmploymentStepRequest,
  OnboardingFamilyStepRequest,
  OnboardingNomineeEntry,
  OnboardingSocialProfileRequest,
  PastServiceRecordRequest,
  QualificationRequest,
} from '../../../types/api'
import {
  EMPTY_SOCIAL_PROFILE,
  emptyDependent,
  emptyNominee,
  type LocalAddress,
  type LocalBanking,
  type LocalDependent,
  type LocalDocument,
  type LocalEmployment,
  type LocalFamily,
  type LocalNominee,
  type LocalPastService,
  type LocalQualification,
  type LocalSocialProfile,
} from './onboardingTypes'

export function socialProfileToRequest(s: LocalSocialProfile): OnboardingSocialProfileRequest {
  return {
    socialCategory: s.socialCategory,
    subCasteCommunity: s.subCasteCommunity || null,
    isPwbd: s.isPwbd,
    disabilityType: s.isPwbd ? s.disabilityType || null : null,
    disabilityPercentage: s.isPwbd && s.disabilityPercentage ? Number(s.disabilityPercentage) : null,
    isExServiceman: s.isExServiceman,
    isSportsQuota: s.isSportsQuota,
  }
}

export function socialProfileFromResponse(r: OnboardingSocialProfileRequest | null): LocalSocialProfile {
  if (!r) return EMPTY_SOCIAL_PROFILE
  return {
    socialCategory: r.socialCategory,
    subCasteCommunity: r.subCasteCommunity ?? '',
    isPwbd: r.isPwbd,
    disabilityType: r.disabilityType ?? '',
    disabilityPercentage: r.disabilityPercentage != null ? String(r.disabilityPercentage) : '',
    isExServiceman: r.isExServiceman,
    isSportsQuota: r.isSportsQuota,
  }
}

export function addressToRequest(a: LocalAddress): EmployeeAddressRequest {
  return {
    addressType: a.addressType,
    addressLine1: a.addressLine1,
    addressLine2: a.addressLine2 || null,
    postOffice: a.postOffice || null,
    policeStation: a.policeStation || null,
    city: a.city,
    district: a.district,
    state: a.state,
    pinCode: a.pinCode,
  }
}

export function addressFromResponse(r: EmployeeAddressRequest | null, type: 'PRESENT' | 'PERMANENT'): LocalAddress {
  if (!r) return { addressType: type, addressLine1: '', addressLine2: '', postOffice: '', policeStation: '', city: '', district: '', state: '', pinCode: '' }
  return {
    addressType: type,
    addressLine1: r.addressLine1,
    addressLine2: r.addressLine2 ?? '',
    postOffice: r.postOffice ?? '',
    policeStation: r.policeStation ?? '',
    city: r.city,
    district: r.district,
    state: r.state,
    pinCode: r.pinCode,
  }
}

export function bankingToRequest(b: LocalBanking): EmployeeBankAccountRequest {
  return {
    bankName: b.bankName,
    bankBranch: b.bankBranch,
    bankAccountNumber: b.bankAccountNumber,
    reenterBankAccountNumber: b.reenterBankAccountNumber,
    bankIfsc: b.bankIfsc,
    cancelledChequeS3Key: b.cancelledChequeS3Key || null,
  }
}

export function bankingFromResponse(r: EmployeeBankAccountRequest | null): LocalBanking {
  if (!r) return { bankName: '', bankBranch: '', bankAccountNumber: '', reenterBankAccountNumber: '', bankIfsc: '', cancelledChequeS3Key: '' }
  return {
    bankName: r.bankName,
    bankBranch: r.bankBranch,
    bankAccountNumber: r.bankAccountNumber,
    reenterBankAccountNumber: r.reenterBankAccountNumber,
    bankIfsc: r.bankIfsc,
    cancelledChequeS3Key: r.cancelledChequeS3Key ?? '',
  }
}

export function qualificationToRequest(q: LocalQualification): QualificationRequest {
  return {
    qualificationLevel: q.qualificationLevel,
    degreeTitle: q.degreeTitle,
    specialization: q.specialization || null,
    boardUniversity: q.boardUniversity,
    institutionName: q.institutionName || null,
    passingYear: Number(q.passingYear),
    percentageCgpa: q.percentageCgpa ? Number(q.percentageCgpa) : null,
    divisionClass: q.divisionClass || null,
    courseType: q.courseType,
    highestQualification: q.highestQualification,
    certificateDocumentS3Key: q.certificateDocumentS3Key || null,
  }
}

export function qualificationsFromResponse(list: QualificationRequest[] | null): LocalQualification[] {
  return (list ?? []).map((q) => ({
    key: crypto.randomUUID(),
    qualificationLevel: q.qualificationLevel,
    degreeTitle: q.degreeTitle,
    specialization: q.specialization ?? '',
    boardUniversity: q.boardUniversity,
    institutionName: q.institutionName ?? '',
    passingYear: String(q.passingYear),
    percentageCgpa: q.percentageCgpa != null ? String(q.percentageCgpa) : '',
    divisionClass: q.divisionClass ?? '',
    courseType: q.courseType,
    highestQualification: q.highestQualification,
    certificateDocumentS3Key: q.certificateDocumentS3Key ?? '',
  }))
}

export function pastServiceToRequest(p: LocalPastService): PastServiceRecordRequest {
  return {
    organizationName: p.organizationName,
    organizationType: p.organizationType,
    designationHeld: p.designationHeld,
    fromDate: p.fromDate,
    toDate: p.toDate,
    lastPayScalePattern: p.lastPayScalePattern || null,
    lastDrawnBasic: p.lastDrawnBasic ? Number(p.lastDrawnBasic) : null,
    lastDrawnGross: p.lastDrawnGross ? Number(p.lastDrawnGross) : null,
    qualifyingForPensionGratuity: p.qualifyingForPensionGratuity,
    qualifyingServiceOrderRef: p.qualifyingServiceOrderRef || null,
    reasonForLeaving: p.reasonForLeaving || null,
    experienceCertificateS3Key: p.experienceCertificateS3Key || null,
    relievingNocDocumentS3Key: p.relievingNocDocumentS3Key || null,
  }
}

export function pastServiceFromResponse(list: PastServiceRecordRequest[] | null): LocalPastService[] {
  return (list ?? []).map((p) => ({
    key: crypto.randomUUID(),
    organizationName: p.organizationName,
    organizationType: p.organizationType,
    designationHeld: p.designationHeld,
    fromDate: p.fromDate,
    toDate: p.toDate,
    lastPayScalePattern: p.lastPayScalePattern ?? '',
    lastDrawnBasic: p.lastDrawnBasic != null ? String(p.lastDrawnBasic) : '',
    lastDrawnGross: p.lastDrawnGross != null ? String(p.lastDrawnGross) : '',
    qualifyingForPensionGratuity: p.qualifyingForPensionGratuity,
    qualifyingServiceOrderRef: p.qualifyingServiceOrderRef ?? '',
    reasonForLeaving: p.reasonForLeaving ?? '',
    experienceCertificateS3Key: p.experienceCertificateS3Key ?? '',
    relievingNocDocumentS3Key: p.relievingNocDocumentS3Key ?? '',
  }))
}

export function employmentToRequest(e: LocalEmployment): OnboardingEmploymentStepRequest {
  return {
    employmentCategory: e.employmentCategory,
    departmentId: e.departmentId ? Number(e.departmentId) : null,
    designationId: e.designationId ? Number(e.designationId) : null,
    postId: e.postId ? Number(e.postId) : null,
    // Legacy pay_scale_master no longer collected in the wizard - REGULAR onboarding now carries
    // pay/grade purely via scaleCode (grade_scale_master, V48-51); see backend
    // OnboardingEmploymentStepRequest.payScaleId's javadoc for why the field itself still exists.
    payScaleId: null,
    regularBasicPay: e.regularBasicPay ? Number(e.regularBasicPay) : null,
    dailyWageRate: e.dailyWageRate ? Number(e.dailyWageRate) : null,
    wageRevisionOrderNo: e.wageRevisionOrderNo || null,
    fixedLumpSumMonthly: e.fixedLumpSumMonthly ? Number(e.fixedLumpSumMonthly) : null,
    contractStartDate: e.contractStartDate || null,
    contractEndDate: e.contractEndDate || null,
    contractRefOrder: e.contractRefOrder || null,
    vendorId: e.vendorId ? Number(e.vendorId) : null,
    monthlyCtc: e.monthlyCtc ? Number(e.monthlyCtc) : null,
    billingRateMonthly: e.billingRateMonthly ? Number(e.billingRateMonthly) : null,
    agencyEmployeeId: e.agencyEmployeeId || null,
    ctcBreakdown: e.ctcBreakdown.map((c) => ({ headCode: c.headCode, headName: c.headName, headType: c.headType, amount: Number(c.amount) })),
    scaleCode: e.scaleCode || null,
    incrementCycle: e.employmentCategory === 'REGULAR' ? e.incrementCycle : null,
    isNpsEligible: e.isNpsEligible,
    isEpsEligible: e.isEpsEligible,
    isEpsHigherPensionEligible: e.isEpsEligible ? e.isEpsHigherPensionEligible : false,
    pranNumber: e.pranNumber || null,
    dateOfJoiningPsu: e.dateOfJoiningPsu,
    recruitmentYear: Number(e.recruitmentYear),
    recruitmentMode: e.recruitmentMode,
    selectionMethod: e.selectionMethod,
    recruitmentAgency: e.recruitmentAgency || null,
    advertisementNo: e.advertisementNo || null,
    appointmentLetterNo: e.appointmentLetterNo,
    appointmentLetterDate: e.appointmentLetterDate,
    offerLetterDate: e.offerLetterDate || null,
    joiningLetterDate: e.joiningLetterDate,
  }
}

export function employmentFromResponse(r: OnboardingEmploymentStepRequest | null): LocalEmployment {
  if (!r) {
    return {
      employmentCategory: 'REGULAR', departmentId: '', designationId: '', postId: '', regularBasicPay: '', scaleCode: '',
      incrementCycle: 'JULY',
      isNpsEligible: true, isEpsEligible: false, isEpsHigherPensionEligible: false, pranNumber: '',
      dailyWageRate: '', wageRevisionOrderNo: '', fixedLumpSumMonthly: '', contractStartDate: '', contractEndDate: '',
      contractRefOrder: '', vendorId: '', monthlyCtc: '', billingRateMonthly: '', agencyEmployeeId: '', ctcBreakdown: [],
      dateOfJoiningPsu: '', recruitmentYear: '', recruitmentMode: 'DIRECT_RECRUITMENT', selectionMethod: '',
      recruitmentAgency: '', advertisementNo: '', appointmentLetterNo: '', appointmentLetterDate: '', offerLetterDate: '',
      joiningLetterDate: '',
    }
  }
  return {
    employmentCategory: r.employmentCategory,
    departmentId: r.departmentId ? String(r.departmentId) : '',
    designationId: r.designationId ? String(r.designationId) : '',
    postId: r.postId ? String(r.postId) : '',
    regularBasicPay: r.regularBasicPay != null ? String(r.regularBasicPay) : '',
    scaleCode: r.scaleCode ?? '',
    incrementCycle: r.incrementCycle ?? 'JULY',
    isNpsEligible: r.isNpsEligible ?? true,
    isEpsEligible: r.isEpsEligible ?? false,
    isEpsHigherPensionEligible: r.isEpsHigherPensionEligible ?? false,
    pranNumber: r.pranNumber ?? '',
    dailyWageRate: r.dailyWageRate != null ? String(r.dailyWageRate) : '',
    wageRevisionOrderNo: r.wageRevisionOrderNo ?? '',
    fixedLumpSumMonthly: r.fixedLumpSumMonthly != null ? String(r.fixedLumpSumMonthly) : '',
    contractStartDate: r.contractStartDate ?? '',
    contractEndDate: r.contractEndDate ?? '',
    contractRefOrder: r.contractRefOrder ?? '',
    vendorId: r.vendorId ? String(r.vendorId) : '',
    monthlyCtc: r.monthlyCtc != null ? String(r.monthlyCtc) : '',
    billingRateMonthly: r.billingRateMonthly != null ? String(r.billingRateMonthly) : '',
    agencyEmployeeId: r.agencyEmployeeId ?? '',
    ctcBreakdown: (r.ctcBreakdown ?? []).map((c) => ({ key: crypto.randomUUID(), headCode: c.headCode, headName: c.headName, headType: c.headType, amount: String(c.amount) })),
    dateOfJoiningPsu: r.dateOfJoiningPsu,
    recruitmentYear: String(r.recruitmentYear),
    recruitmentMode: r.recruitmentMode,
    selectionMethod: r.selectionMethod,
    recruitmentAgency: r.recruitmentAgency ?? '',
    advertisementNo: r.advertisementNo ?? '',
    appointmentLetterNo: r.appointmentLetterNo,
    appointmentLetterDate: r.appointmentLetterDate,
    offerLetterDate: r.offerLetterDate ?? '',
    joiningLetterDate: r.joiningLetterDate,
  }
}

function dependentToRequest(d: LocalDependent): OnboardingDependentEntry {
  return { name: d.name, relationship: d.relationship, dateOfBirth: d.dateOfBirth || null, isDependent: d.isDependent, isCoveredMedical: d.isCoveredMedical }
}

function nomineeToRequest(n: LocalNominee): OnboardingNomineeEntry {
  return { name: n.name, relationship: n.relationship, sharePercentage: Number(n.sharePercentage), nomineeFor: n.nomineeFor }
}

export function familyToRequest(f: LocalFamily): OnboardingFamilyStepRequest {
  return {
    fatherName: f.fatherName,
    motherName: f.motherName || null,
    spouseName: f.spouseName || null,
    spouseDob: f.spouseDob || null,
    dependents: f.dependents.map(dependentToRequest),
    nominees: f.nominees.map(nomineeToRequest),
  }
}

export function familyFromResponse(r: OnboardingFamilyStepRequest | null): LocalFamily {
  if (!r) return { fatherName: '', motherName: '', spouseName: '', spouseDob: '', dependents: [], nominees: [] }
  return {
    fatherName: r.fatherName,
    motherName: r.motherName ?? '',
    spouseName: r.spouseName ?? '',
    spouseDob: r.spouseDob ?? '',
    dependents: (r.dependents ?? []).map((d) => ({ ...emptyDependent(), name: d.name, relationship: d.relationship, dateOfBirth: d.dateOfBirth ?? '', isDependent: d.isDependent, isCoveredMedical: d.isCoveredMedical })),
    nominees: (r.nominees ?? []).map((n) => ({ ...emptyNominee(), name: n.name, relationship: n.relationship, sharePercentage: String(n.sharePercentage), nomineeFor: n.nomineeFor })),
  }
}

export function documentToRequest(d: LocalDocument): OnboardingDocumentEntry {
  return { documentCategory: d.documentCategory, documentTitle: d.documentTitle, fileS3Key: d.fileS3Key, mimeType: d.mimeType || null }
}

export function documentsFromResponse(list: OnboardingDocumentEntry[] | null): LocalDocument[] {
  return (list ?? []).map((d) => ({
    key: crypto.randomUUID(),
    documentCategory: d.documentCategory,
    documentTitle: d.documentTitle,
    fileS3Key: d.fileS3Key,
    mimeType: d.mimeType ?? '',
    originalFileName: d.fileS3Key.split('/').pop() ?? d.documentTitle,
  }))
}
