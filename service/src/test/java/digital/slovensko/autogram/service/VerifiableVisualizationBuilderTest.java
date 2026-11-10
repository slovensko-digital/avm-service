package digital.slovensko.autogram.service;

import com.google.gson.Gson;
import digital.slovensko.autogram.core.Responder;
import digital.slovensko.autogram.core.Settings;
import digital.slovensko.autogram.core.dto.DataToSignStructure;
import digital.slovensko.autogram.core.dto.SignedDocument;
import digital.slovensko.autogram.core.errors.AutogramException;
import digital.slovensko.autogram.core.server.dto.SignRequestBody;
import digital.slovensko.autogram.service.dto.VerifiableVisualizationResponse;
import digital.slovensko.autogram.service.errors.VerifiableVisualizationNotSupportedException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class VerifiableVisualizationBuilderTest {
    private static final String XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String ASIC_NS = "http://uri.etsi.org/02918/v1.2.1#";
    private static final String XDC_NS = "http://data.gov.sk/def/container/xmldatacontainer+xml/1.1";
    private static final byte[] TXT = "Ahoj svet!\nŽiadosť o podpis.\n".getBytes(StandardCharsets.UTF_8);

    private static final AutogramService service = new AutogramService(new Settings());
    private static TestSigner signer;
    private static TestSigner otherSigner;

    @BeforeAll
    static void setUp() throws Exception {
        signer = TestSigner.create("Test Signer");
        otherSigner = TestSigner.create("Other Signer");
    }

    @Test
    void xadesVisualizationContainsDocumentReferencedFromSignedInfo() throws Exception {
        var request = request("document.txt", TXT, "text/plain;base64", Map.of("level", "XAdES_BASELINE_B", "container", "ASiC_E"));
        var visualization = visualize(request);

        assertEquals("XAdES", visualization.signatureForm());
        assertEquals("document.txt", visualization.document().filename());
        assertArrayEquals(TXT, content(visualization.document()));
        assertNull(visualization.transformation());
        assertNull(visualization.signingTime());

        var reference = getDataObjectReference(dataToSign(request, signer, null));
        assertEquals("document.txt", reference.getAttribute("URI"));
        assertEquals(base64(sha256(TXT)), getDigestValue(reference));
    }

    @Test
    void xadesVisualizationOfEFormContainsXdcAndCanonicalizedTransformation() throws Exception {
        var parameters = new HashMap<String, Object>(Map.of(
                "level", "XAdES_BASELINE_B",
                "container", "ASiC_E",
                "identifier", "http://data.gov.sk/doc/eform/App.GeneralAgenda/1.9",
                "containerXmlns", XDC_NS,
                "schema", base64(resource("general_agenda.xsd")),
                "transformation", base64(resource("general_agenda.xslt"))));
        var request = request("general_agenda.xml", resource("general_agenda.xml"), "application/xml;base64", parameters);
        var visualization = visualize(request);

        assertEquals("general_agenda.xdcf", visualization.document().filename());
        assertTrue(visualization.document().mimeType().startsWith("application/vnd.gov.sk.xmldatacontainer+xml"));
        var xdc = content(visualization.document());
        assertEquals(base64(sha256(xdc)), getDigestValue(getDataObjectReference(dataToSign(request, signer, null))));

        assertEquals("TXT", visualization.transformation().mediaDestinationTypeDescription());
        var transformation = Base64.getDecoder().decode(visualization.transformation().content());
        var presentationSchemaReference = (Element) parseXml(xdc)
                .getElementsByTagNameNS(XDC_NS, "UsedPresentationSchemaReference").item(0);
        assertEquals(presentationSchemaReference.getAttribute("DigestValue"), base64(sha256(transformation)));
    }

    @Test
    void cadesVisualizationContainsManifestDigestedByDataToSign() throws Exception {
        var request = request("document.txt", TXT, "text/plain;base64", Map.of("level", "CAdES_BASELINE_B", "container", "ASiC_E"));
        var visualization = visualize(request);

        assertEquals("CAdES", visualization.signatureForm());
        assertArrayEquals(TXT, content(visualization.document()));

        var manifest = content(visualization.asicManifest());
        assertArrayEquals(sha256(manifest), getMessageDigest(dataToSign(request, signer, null)));

        var dataObjectReferences = parseXml(manifest).getElementsByTagNameNS(ASIC_NS, "DataObjectReference");
        assertEquals(1, dataObjectReferences.getLength());
        var dataObjectReference = (Element) dataObjectReferences.item(0);
        assertEquals("document.txt", dataObjectReference.getAttribute("URI"));
        assertEquals(base64(sha256(TXT)), getDigestValue(dataObjectReference));
    }

    @Test
    void cadesWithoutContainerDigestsDocumentDirectly() throws Exception {
        var request = request("document.txt", TXT, "text/plain;base64", Map.of("level", "CAdES_BASELINE_B"));
        var visualization = visualize(request);

        assertNull(visualization.asicManifest());
        assertArrayEquals(sha256(TXT), getMessageDigest(dataToSign(request, signer, null)));
    }

    @Test
    void padesVisualizationContainsPdfDigestedByDataToSignOfTheSameSigningTime() throws Exception {
        var pdf = createPdf();
        var request = request("document.pdf", pdf, "application/pdf;base64", Map.of("level", "PAdES_BASELINE_B"));
        var visualization = visualize(request);

        assertEquals("PAdES", visualization.signatureForm());
        assertNotNull(visualization.signingTime());
        var preparedPdf = content(visualization.document());
        assertArrayEquals(pdf, Arrays.copyOf(preparedPdf, pdf.length));

        var byteRange = visualization.pdfByteRange();
        assertEquals(0, byteRange.get(0));
        assertEquals(preparedPdf.length, byteRange.get(2) + byteRange.get(3));
        var signatureContents = new String(preparedPdf, byteRange.get(1).intValue(),
                (int) (byteRange.get(2) - byteRange.get(1)), StandardCharsets.ISO_8859_1);
        assertTrue(signatureContents.matches("<0+>"));
        assertEquals(VerifiableSigningJob.PADES_CONTENT_SIZE * 2 + 2, signatureContents.length());

        var digest = sha256(Arrays.copyOfRange(preparedPdf, 0, byteRange.get(1).intValue()),
                Arrays.copyOfRange(preparedPdf, byteRange.get(2).intValue(), preparedPdf.length));

        // prepared PDF does not depend on signing certificate nor on signature level
        assertArrayEquals(digest, getMessageDigest(dataToSign(request, signer, visualization.signingTime())));
        assertArrayEquals(digest, getMessageDigest(dataToSign(request, otherSigner, visualization.signingTime())));
        var timestampRequest = request("document.pdf", pdf, "application/pdf;base64", Map.of("level", "PAdES_BASELINE_T"));
        assertArrayEquals(digest, getMessageDigest(dataToSign(timestampRequest, signer, visualization.signingTime())));

        // but it depends on signing time
        assertFalse(Arrays.equals(digest, getMessageDigest(dataToSign(request, signer, visualization.signingTime() + 1000))));
    }

    @Test
    void padesSignedDocumentEqualsVisualizedPdfOutsideOfSignature() throws Exception {
        var request = request("document.pdf", createPdf(), "application/pdf;base64", Map.of("level", "PAdES_BASELINE_B"));
        var visualization = visualize(request);
        var dataToSign = service.getDataToSign(request.getDocument(), request.getParameters(), signer.certificate(),
                visualization.signingTime());

        var signedPdf = content(sign(request, dataToSign, signer));

        var preparedPdf = content(visualization.document());
        var byteRange = visualization.pdfByteRange();
        assertEquals(preparedPdf.length, signedPdf.length);
        assertArrayEquals(Arrays.copyOfRange(preparedPdf, 0, byteRange.get(1).intValue()),
                Arrays.copyOfRange(signedPdf, 0, byteRange.get(1).intValue()));
        assertArrayEquals(Arrays.copyOfRange(preparedPdf, byteRange.get(2).intValue(), preparedPdf.length),
                Arrays.copyOfRange(signedPdf, byteRange.get(2).intValue(), signedPdf.length));
    }

    @Test
    void signsDocumentWithDataToSignOfProvidedSigningTime() throws Exception {
        var request = request("document.txt", TXT, "text/plain;base64", Map.of("level", "XAdES_BASELINE_B", "container", "ASiC_E"));
        var signingTime = new Date().getTime() - 60_000;
        var dataToSign = service.getDataToSign(request.getDocument(), request.getParameters(), signer.certificate(), signingTime);

        assertEquals(signingTime, dataToSign.signingTime());
        assertEquals("application/vnd.etsi.asic-e+zip", sign(request, dataToSign, signer).getMimeType().getMimeTypeString());
    }

    @Test
    void xadesWithoutContainerIsNotSupported() {
        var request = request("document.xml", "<a xmlns=\"urn:test\">text</a>".getBytes(StandardCharsets.UTF_8),
                "application/xml;base64", Map.of("level", "XAdES_BASELINE_B"));

        assertThrows(VerifiableVisualizationNotSupportedException.class, () -> visualize(request));
    }

    private static VerifiableVisualizationResponse visualize(SignRequestBody request) {
        return service.buildVerifiableVisualization(request.getDocument(), request.getParameters());
    }

    private static byte[] dataToSign(SignRequestBody request, TestSigner signer, Long signingTime) {
        var dataToSign = service.getDataToSign(request.getDocument(), request.getParameters(), signer.certificate(), signingTime);
        return Base64.getDecoder().decode(dataToSign.dataToSign());
    }

    private static eu.europa.esig.dss.model.DSSDocument sign(SignRequestBody request, DataToSignStructure dataToSign,
                                                             TestSigner signer) throws Exception {
        var signedData = signer.sign(Base64.getDecoder().decode(dataToSign.dataToSign()));
        var result = new SignedDocument[1];
        service.buildSignedDocument(request.getDocument(), request.getParameters(), dataToSign, base64(signedData), new Responder() {
            @Override
            public void onSuccess(SignedDocument signedDocument) {
                result[0] = signedDocument;
            }

            @Override
            public void onError(AutogramException error) {
                fail(error);
            }
        });

        return Objects.requireNonNull(result[0]).dssDocument();
    }

    private static SignRequestBody request(String filename, byte[] content, String payloadMimeType, Map<String, ?> parameters) {
        var json = Map.of(
                "document", Map.of("filename", filename, "content", base64(content)),
                "parameters", parameters,
                "payloadMimeType", payloadMimeType);
        var gson = new Gson();
        var request = gson.fromJson(gson.toJson(json), SignRequestBody.class);
        request.validateDocument();
        request.validateSigningParameters();

        return request;
    }

    private static Element getDataObjectReference(byte[] signedInfo) throws Exception {
        var references = parseXml(signedInfo).getElementsByTagNameNS(XMLDSIG_NS, "Reference");
        assertEquals(2, references.getLength());

        var dataObjectReference = (Element) references.item(0);
        assertFalse(dataObjectReference.getAttribute("URI").startsWith("#"));
        assertEquals("http://uri.etsi.org/01903#SignedProperties", ((Element) references.item(1)).getAttribute("Type"));

        return dataObjectReference;
    }

    private static String getDigestValue(Element reference) {
        return reference.getElementsByTagNameNS(XMLDSIG_NS, "DigestValue").item(0).getTextContent();
    }

    private static byte[] getMessageDigest(byte[] signedAttributes) {
        for (var attribute : ASN1Set.getInstance(signedAttributes)) {
            var attr = Attribute.getInstance(attribute);
            if (attr.getAttrType().equals(CMSAttributes.messageDigest))
                return ASN1OctetString.getInstance(attr.getAttrValues().getObjectAt(0)).getOctets();
        }

        throw new AssertionError("message-digest attribute not found");
    }

    private static org.w3c.dom.Document parseXml(byte[] xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static byte[] content(digital.slovensko.autogram.core.server.dto.Document document) {
        return Base64.getDecoder().decode(document.content());
    }

    private static byte[] content(eu.europa.esig.dss.model.DSSDocument document) throws IOException {
        try (var stream = document.openStream()) {
            return stream.readAllBytes();
        }
    }

    private static byte[] resource(String name) throws IOException {
        try (var stream = VerifiableVisualizationBuilderTest.class.getResourceAsStream(name)) {
            return Objects.requireNonNull(stream).readAllBytes();
        }
    }

    private static byte[] createPdf() throws IOException {
        try (var pdf = new PDDocument()) {
            pdf.addPage(new PDPage());

            var output = new ByteArrayOutputStream();
            pdf.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] sha256(byte[]... parts) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        for (var part : parts)
            digest.update(part);

        return digest.digest();
    }

    private static String base64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    private record TestSigner(KeyPair keyPair, String certificate) {
        static TestSigner create(String commonName) throws Exception {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var keyPair = generator.generateKeyPair();

            var name = new X500Name("CN=" + commonName + ", C=SK");
            var now = System.currentTimeMillis();
            var builder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), new Date(now - 86_400_000L),
                    new Date(now + 86_400_000L), name, keyPair.getPublic());
            var certificate = new JcaX509CertificateConverter()
                    .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));

            return new TestSigner(keyPair, base64(certificate.getEncoded()));
        }

        byte[] sign(byte[] data) throws Exception {
            var signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(data);
            return signature.sign();
        }
    }
}
