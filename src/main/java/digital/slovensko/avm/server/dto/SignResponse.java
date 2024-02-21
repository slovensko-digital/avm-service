package digital.slovensko.avm.server.dto;

import java.lang.reflect.Array;

public record SignResponse(DocumentResponse documentResponse, SignerRecord signer) {
}
