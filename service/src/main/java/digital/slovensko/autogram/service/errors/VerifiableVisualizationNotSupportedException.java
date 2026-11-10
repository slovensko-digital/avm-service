package digital.slovensko.autogram.service.errors;

import digital.slovensko.autogram.core.errors.AutogramException;

public class VerifiableVisualizationNotSupportedException extends AutogramException {
    public VerifiableVisualizationNotSupportedException(String signatureType) {
        super("Nepodporovaný typ podpisu", "Overiteľná vizualizácia nie je podporovaná pre typ podpisu: " + signatureType,
                "Overiteľnú vizualizáciu je možné vytvoriť pre XAdES v ASiC kontajneri, CAdES a PAdES podpisy.");
    }
}
