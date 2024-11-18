package digital.slovensko.avm.core.errors;

public class TransformationParsingErrorException extends AutogramException {
    public TransformationParsingErrorException(String message) {
        super("Nastala chyba", "Nastala chyba pri čítaní XSLT transformácie", message);
    }
}
