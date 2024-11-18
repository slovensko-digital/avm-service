package digital.slovensko.avm.core.errors;

import digital.slovensko.avm.core.errors.AutogramException;

public class EmptyBodyException extends AutogramException {
    public EmptyBodyException(String message) {
        super("Empty body", "JsonSyntaxException parsing request body.", message);
    }
}
