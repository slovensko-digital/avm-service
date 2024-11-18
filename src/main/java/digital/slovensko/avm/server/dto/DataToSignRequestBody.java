package digital.slovensko.avm.server.dto;

public record DataToSignRequestBody(OriginalSignRequestBody originalSignRequestBody, String signingCertificate) {
}
