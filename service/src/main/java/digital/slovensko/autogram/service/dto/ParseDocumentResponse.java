package digital.slovensko.autogram.service.dto;

import java.util.ArrayList;

public record ParseDocumentResponse (String filename, String contentType, ArrayList<ParsedSubdocument> subdocuments) {
}

