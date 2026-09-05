package in.gov.jci.hrms.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidation_populatesFieldErrorsAlongsideFlatMessage() {
        FieldError panError = new FieldError("employeeRequest", "panNumber", "must match \"^[A-Z]{5}[0-9]{4}[A-Z]$\"");
        FieldError phoneError = new FieldError("employeeRequest", "phone", "must not be blank");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(panError, phoneError));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("panNumber: must match \"^[A-Z]{5}[0-9]{4}[A-Z]$\"; phone: must not be blank");
        assertThat(body.fieldErrors()).containsExactly(
                new FieldErrorDetail("panNumber", "must match \"^[A-Z]{5}[0-9]{4}[A-Z]$\""),
                new FieldErrorDetail("phone", "must not be blank"));
    }

    @Test
    void handleNotFound_leavesFieldErrorsEmpty() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new in.gov.jci.hrms.exception.EmployeeNotFoundException(1L));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors()).isEmpty();
    }
}
