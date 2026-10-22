package digital.slovensko.autogram.service;

import digital.slovensko.autogram.core.SigningJob;
import digital.slovensko.autogram.core.SigningParameters;
import digital.slovensko.autogram.core.eforms.EFormUtils;
import digital.slovensko.autogram.core.errors.TransformationException;
import digital.slovensko.autogram.core.server.dto.Document;
import digital.slovensko.autogram.core.util.AsicContainerUtils;
import digital.slovensko.autogram.core.util.XMLUtils;
import digital.slovensko.autogram.service.dto.VerifiableVisualizationResponse;
import digital.slovensko.autogram.service.errors.VerifiableVisualizationNotSupportedException;
import eu.europa.esig.dss.asic.cades.ASiCWithCAdESSignatureParameters;
import eu.europa.esig.dss.asic.cades.DefaultASiCWithCAdESFilenameFactory;
import eu.europa.esig.dss.asic.cades.signature.ASiCWithCAdESASiCContentBuilder;
import eu.europa.esig.dss.asic.cades.signature.ASiCWithCAdESSignatureDataToSignHelperBuilder;
import eu.europa.esig.dss.enumerations.ASiCContainerType;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.xml.utils.XMLCanonicalizer;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import static digital.slovensko.autogram.core.AutogramMimeType.isAsice;
import static digital.slovensko.autogram.core.AutogramMimeType.isXDC;

/**
 * Builds {@link VerifiableVisualizationResponse} - data objects covered by the signature, from which the client can
 * visualize the document by itself and verify that DataToSign corresponds to the visualized document:
 * <ul>
 *     <li>XAdES in ASiC container - the signed document, its digest is in the reference of the SignedInfo (DataToSign).
 *     XML Datacontainer is returned with canonicalized XSLT, whose digest is in the XML Datacontainer.</li>
 *     <li>CAdES - the signed document and for ASiC-E the ASiC manifest with document's digest, digest of the manifest
 *     (or of the document itself) is the message-digest signed attribute (DataToSign).</li>
 *     <li>PAdES - the PDF prepared for signing and its /ByteRange, digest of the byte range is the message-digest
 *     signed attribute (DataToSign). The PDF is prepared with the returned signing time, which has to be used for
 *     DataToSign too.</li>
 * </ul>
 */
public class VerifiableVisualizationBuilder {
    private static final String XDC_XMLNS = EFormUtils.XDC_XMLNS;
    private static final Pattern BYTE_RANGE_PATTERN = Pattern.compile("/ByteRange\\s*\\[\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*]");

    private final SigningJob job;

    private VerifiableVisualizationBuilder(SigningJob job) {
        this.job = job;
    }

    public static VerifiableVisualizationResponse build(SigningJob job) {
        return new VerifiableVisualizationBuilder(job).build();
    }

    private VerifiableVisualizationResponse build() {
        return switch (job.getParameters().getSignatureType()) {
            case XAdES -> buildForXAdES();
            case CAdES -> buildForCAdES();
            case PAdES -> buildForPAdES();
            default -> throw new VerifiableVisualizationNotSupportedException(job.getParameters().getSignatureType().name());
        };
    }

    private VerifiableVisualizationResponse buildForXAdES() {
        // XAdES without container references the document by an enveloped or enveloping reference with transforms,
        // client would have to canonicalize XML to verify its digest
        if (job.getParameters().getContainer() == null)
            throw new VerifiableVisualizationNotSupportedException("XAdES bez ASiC kontajnera");

        var signedDocument = getSignedDocument();
        var transformation = isXDC(signedDocument.getMimeType()) ? buildTransformation(signedDocument) : null;

        return new VerifiableVisualizationResponse("XAdES", toResponseDocument(signedDocument), transformation, null,
                null, null);
    }

    private VerifiableVisualizationResponse buildForCAdES() {
        var signedDocument = getSignedDocument();
        Document asicManifest = null;

        if (job.getParameters().getContainer() == ASiCContainerType.ASiC_E) {
            var parameters = (ASiCWithCAdESSignatureParameters) job.getParameters().getSignatureParameters();
            var asicContent = new ASiCWithCAdESASiCContentBuilder()
                    .build(List.of(job.getDocument()), ASiCContainerType.ASiC_E);
            var manifest = new ASiCWithCAdESSignatureDataToSignHelperBuilder(new DefaultASiCWithCAdESFilenameFactory())
                    .build(asicContent, parameters)
                    .getToBeSigned();

            asicManifest = new Document(manifest.getName(), encode(manifest), "text/xml;base64");
        }

        return new VerifiableVisualizationResponse("CAdES", toResponseDocument(signedDocument), null, asicManifest,
                null, null);
    }

    private VerifiableVisualizationResponse buildForPAdES() {
        var signingTime = new Date();
        var pdf = new VerifiableSigningJob(job).buildPdfToBeSigned(signingTime);
        var content = readBytes(pdf);

        var document = new Document(job.getDocument().getName(), Base64.getEncoder().encodeToString(content),
                "application/pdf;base64");

        return new VerifiableVisualizationResponse("PAdES", document, null, null, findByteRange(content),
                signingTime.getTime());
    }

    /**
     * Gets the document which will be referenced from the signature - the document itself, XML Datacontainer created
     * from XML document or the original document from already signed ASiC-E container.
     */
    private DSSDocument getSignedDocument() {
        var document = job.getDocument();
        if (isAsice(document.getMimeType()))
            return AsicContainerUtils.getOriginalDocument(document);

        return document;
    }

    private VerifiableVisualizationResponse.Transformation buildTransformation(DSSDocument xdc) {
        var transformation = job.getParameters().getTransformation();
        if (transformation == null)
            return null;

        Element reference;
        try (var stream = xdc.openStream()) {
            var xdcDocument = XMLUtils.getSecureDocumentBuilder().parse(new InputSource(stream));
            reference = (Element) xdcDocument.getElementsByTagNameNS(XDC_XMLNS, "UsedPresentationSchemaReference").item(0);
        } catch (Exception e) {
            throw new TransformationException("Nastala chyba počas čítania XML Datacontainera", "XML Datacontainer sa nepodarilo načítať", e);
        }

        // embedded presentation schema is part of the XML Datacontainer itself
        if (reference == null)
            return null;

        var destinationType = reference.getAttribute("MediaDestinationTypeDescription");
        if (destinationType.isEmpty())
            destinationType = job.getParameters().getXsltDestinationType();

        return new VerifiableVisualizationResponse.Transformation(
                Base64.getEncoder().encodeToString(canonicalize(transformation, reference.getAttribute("TransformAlgorithm"), job.getParameters())),
                destinationType);
    }

    /**
     * Canonicalizes XSLT the same way as its digest in XML Datacontainer is computed, so the client can verify the
     * digest from the returned bytes.
     */
    private static byte[] canonicalize(String transformation, String canonicalizationMethod, SigningParameters parameters) {
        if (canonicalizationMethod.isEmpty())
            canonicalizationMethod = parameters.getPropertiesCanonicalization();

        try {
            var builder = parameters.isTrustedXsltSource()
                    ? XMLUtils.getSecureStylesheetDocumentBuilder()
                    : XMLUtils.getSecureDocumentBuilder();
            var inputSource = new InputSource(new ByteArrayInputStream(transformation.getBytes(StandardCharsets.UTF_8)));
            inputSource.setEncoding(StandardCharsets.UTF_8.displayName());

            return XMLCanonicalizer.createInstance(canonicalizationMethod).canonicalize(builder.parse(inputSource));
        } catch (Exception e) {
            throw new TransformationException("Nastala chyba počas spracovania XSLT transformácie", "XSLT transformáciu sa nepodarilo kanonikalizovať", e);
        }
    }

    static List<Long> findByteRange(byte[] pdf) {
        var matcher = BYTE_RANGE_PATTERN.matcher(new String(pdf, StandardCharsets.ISO_8859_1));
        List<Long> byteRange = null;
        while (matcher.find())
            byteRange = List.of(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)),
                    Long.parseLong(matcher.group(3)), Long.parseLong(matcher.group(4)));

        if (byteRange == null)
            throw new IllegalStateException("ByteRange not found in PDF prepared for signing");

        return byteRange;
    }

    private static Document toResponseDocument(DSSDocument document) {
        return new Document(document.getName(), encode(document), document.getMimeType().getMimeTypeString() + ";base64");
    }

    private static String encode(DSSDocument document) {
        return Base64.getEncoder().encodeToString(readBytes(document));
    }

    private static byte[] readBytes(DSSDocument document) {
        try (var stream = document.openStream()) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read document", e);
        }
    }
}
