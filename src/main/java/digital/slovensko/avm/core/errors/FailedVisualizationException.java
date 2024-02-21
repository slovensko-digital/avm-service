package digital.slovensko.avm.core.errors;

public class FailedVisualizationException extends AutogramException {

    public FailedVisualizationException(Throwable e) {
        super(
            "Pri zobrazovaní dokumentu nastala chyba",
            "Chcete pokračovať v podpisovaní?",
            "Pri zobrazovaní dokumentu nastala neočakávaná chyba. Dokument je možné podpísať, ale uistite sa, že dôverujete zdroju dokumentu.\n\nKontaktujte správcu systému a nahláste mu chybu.",
            e);
    }
}
