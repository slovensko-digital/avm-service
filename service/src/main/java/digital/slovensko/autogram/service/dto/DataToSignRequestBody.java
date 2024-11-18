package digital.slovensko.autogram.service.dto;

import digital.slovensko.autogram.core.server.dto.SignRequestBody;

public record DataToSignRequestBody(SignRequestBody originalSignRequestBody, String signingCertificate) {
}
