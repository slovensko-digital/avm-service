package digital.slovensko.avm.core.dto;

import eu.europa.esig.dss.validation.DocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;

public record ReportsAndValidator(Reports reports, DocumentValidator validator) {
}
