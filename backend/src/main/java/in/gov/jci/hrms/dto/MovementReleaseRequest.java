package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.SessionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Tab 2 - Pending Releases modal. Unlike the joining session, the release session IS user-entered (it's a clerical record of the relieving order, not a server-witnessed event). */
public record MovementReleaseRequest(
        @NotBlank @Size(max = 100) String releaseOrderRef,
        @NotNull LocalDate releaseDate,
        @NotNull SessionType releaseSession
) {
}
