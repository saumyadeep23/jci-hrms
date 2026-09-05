package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.VacancyStatus;

import java.time.Instant;

public record PostMasterResponse(
        Long id,
        String postCode,
        String title,
        Long departmentId,
        String departmentName,
        Long designationId,
        String designationTitle,
        Long roId,
        String roName,
        Long dpcId,
        String dpcName,
        Long operationalReportingPostId,
        String operationalReportingPostTitle,
        Long administrativeReportingPostId,
        String administrativeReportingPostTitle,
        Long acceptingAuthorityPostId,
        String acceptingAuthorityPostTitle,
        VacancyStatus vacancyStatus,
        boolean isBudgeted,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static PostMasterResponse from(PostMaster post) {
        RegionalOffice regionalOffice = post.getRegionalOffice();
        DepartmentalPurchaseCentre dpc = post.getDepartmentalPurchaseCentre();
        PostMaster operationalReportingPost = post.getOperationalReportingPost();
        PostMaster administrativeReportingPost = post.getAdministrativeReportingPost();
        PostMaster acceptingAuthorityPost = post.getAcceptingAuthorityPost();

        return new PostMasterResponse(
                post.getId(),
                post.getPostCode(),
                post.getTitle(),
                post.getDepartment().getId(),
                post.getDepartment().getName(),
                post.getDesignation().getId(),
                post.getDesignation().getTitle(),
                regionalOffice != null ? regionalOffice.getId() : null,
                regionalOffice != null ? regionalOffice.getName() : null,
                dpc != null ? dpc.getId() : null,
                dpc != null ? dpc.getName() : null,
                operationalReportingPost != null ? operationalReportingPost.getId() : null,
                operationalReportingPost != null ? operationalReportingPost.getTitle() : null,
                administrativeReportingPost != null ? administrativeReportingPost.getId() : null,
                administrativeReportingPost != null ? administrativeReportingPost.getTitle() : null,
                acceptingAuthorityPost != null ? acceptingAuthorityPost.getId() : null,
                acceptingAuthorityPost != null ? acceptingAuthorityPost.getTitle() : null,
                post.getVacancyStatus(),
                post.isBudgeted(),
                post.isActive(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
