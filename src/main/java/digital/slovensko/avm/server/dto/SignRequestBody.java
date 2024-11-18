package digital.slovensko.avm.server.dto;

import digital.slovensko.avm.core.DataToSignStructure;

public record SignRequestBody(OriginalSignRequestBody originalSignRequestBody, DataToSignStructure dataToSignStructure, String signedData) {
}
