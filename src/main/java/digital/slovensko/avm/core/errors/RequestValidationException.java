package digital.slovensko.avm.core.errors;

import digital.slovensko.avm.core.errors.AutogramException;

public class RequestValidationException extends AutogramException {
    public RequestValidationException(String message, String description) {
        super("Request validation failed", message, description);
    }
}
