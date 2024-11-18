package digital.slovensko.autogram.service.dto;

import digital.slovensko.autogram.core.dto.DataToSignStructure;
import digital.slovensko.autogram.core.server.dto.SignRequestBody;

public record BuildSignatureRequestBody(SignRequestBody originalSignRequestBody, DataToSignStructure dataToSignStructure, String signedData) {
}
