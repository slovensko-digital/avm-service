package digital.slovensko.avm.core.errors;

public class MultipleOriginalDocumentsFoundException extends AutogramException {

    public MultipleOriginalDocumentsFoundException(String description) {
        super("Chyba ASiC-E kontajnera", "Nájdených viacero dokumnetov na podpis", description);
    }
}
