import { Modal } from '../../../../components/common/Modal'
import { EpsEligibilityPanel } from './EpsEligibilityPanel'
import type { CpfTrustMemberResponse } from '../../../../types/api'

export function EpsEligibilityDialog({ member, onClose }: { member: CpfTrustMemberResponse; onClose: () => void }) {
  return (
    <Modal title={`EPS Pension Eligibility - ${member.fullName}`} onClose={onClose}>
      <EpsEligibilityPanel employeeId={member.employeeId} />
    </Modal>
  )
}
