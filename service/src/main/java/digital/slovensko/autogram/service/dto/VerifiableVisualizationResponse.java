package digital.slovensko.autogram.service.dto;

import digital.slovensko.autogram.core.server.dto.Document;

import java.util.List;

/**
 * Data objects covered by the signature, so client can visualize the document by itself and verify that DataToSign
 * corresponds to the visualized document.
 *
 * @param signatureForm XAdES, CAdES or PAdES
 * @param document      signed data object - document referenced from XAdES SignedInfo, from ASiC manifest or from CAdES
 *                      message digest, for PAdES it is the PDF prepared for signing with empty space for signature
 * @param transformation canonicalized XSLT transformation for XML Datacontainer referencing its presentation schema
 * @param asicManifest  ASiC manifest signed by CAdES signature in ASiC-E container
 * @param pdfByteRange  /ByteRange of the PAdES signature in the prepared PDF
 * @param signingTime   signing time used to prepare PDF for PAdES signature, it has to be sent with DataToSign request
 */
public record VerifiableVisualizationResponse(
        String signatureForm,
        Document document,
        Transformation transformation,
        Document asicManifest,
        List<Long> pdfByteRange,
        Long signingTime) {

    /**
     * @param content                          base64 encoded canonicalized XSLT, its digest is in the XML Datacontainer
     * @param mediaDestinationTypeDescription TXT, HTML or XHTML
     */
    public record Transformation(String content, String mediaDestinationTypeDescription) {
    }
}
