package digital.slovensko.autogram.service.dto;

import digital.slovensko.autogram.core.server.dto.SignRequestBody;

/**
 * @param signingTime optional signing time in milliseconds since epoch, current time is used if not provided. Client has
 *                    to provide signing time returned by verifiable visualization of PAdES signature, otherwise the PDF
 *                    prepared for signing would differ from the visualized one.
 */
public record DataToSignRequestBody(SignRequestBody originalSignRequestBody, String signingCertificate, Long signingTime) {
}
