package digital.slovensko.avm.core;

import java.io.File;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Date;

import digital.slovensko.avm.core.eforms.EFormUtils;
import digital.slovensko.avm.core.eforms.XDCBuilder;
import digital.slovensko.avm.core.eforms.XDCValidator;
import digital.slovensko.avm.core.errors.AutogramException;
import digital.slovensko.avm.core.errors.CryptographicSignatureVerificationException;
import digital.slovensko.avm.core.errors.DataToSignMismatchException;
import digital.slovensko.avm.util.DSSUtils;
import eu.europa.esig.dss.asic.cades.signature.ASiCWithCAdESService;
import eu.europa.esig.dss.asic.xades.signature.ASiCWithXAdESService;
import eu.europa.esig.dss.cades.signature.CAdESService;
import eu.europa.esig.dss.enumerations.ASiCContainerType;
import eu.europa.esig.dss.enumerations.SignatureForm;
import eu.europa.esig.dss.jades.signature.JAdESService;
import eu.europa.esig.dss.model.*;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.signature.AbstractSignatureService;
import eu.europa.esig.dss.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.xades.signature.XAdESService;

import static digital.slovensko.avm.core.AutogramMimeType.*;

public class SigningJob {
    private final DSSDocument document;
    private final SigningParameters parameters;

    public SigningJob(DSSDocument document, SigningParameters signingParameters) {
        this.document = document;
        this.parameters = signingParameters;
    }

    public SignedDocument signDocument(DataToSignStructure dataToSignStructure, String signedData) throws AutogramException, CertificateException {
        var token = DSSUtils.parseCertificate(dataToSignStructure.signingCertificate());
        var signatureValue = new SignatureValue(token.getSignatureAlgorithm(), Base64.getDecoder().decode(signedData));
        var commonCertificateVerifier = new CommonCertificateVerifier();
        var signatureParameters = parameters.getSignatureParameters();
        var service = getServiceForSignatureLevel(parameters.getSignatureType(), parameters.getContainer(), commonCertificateVerifier);

        signatureParameters.setSigningCertificate(token);

        if (!signatureParameters.getSignatureLevel().toString().endsWith("_LEVEL_B"))
            service.setTspSource(parameters.getTspSource());

        var bLevelParameters = new BLevelParameters();
        bLevelParameters.setSigningDate(new Date(dataToSignStructure.signingTime()));
        signatureParameters.setBLevelParams(bLevelParameters);

        var dataToSign = service.getDataToSign(document, signatureParameters);
        if (!new String(Base64.getEncoder().encode(dataToSign.getBytes())).equals(dataToSignStructure.dataToSign()))
            throw new DataToSignMismatchException();

        try {
            var doc = service.signDocument(document, signatureParameters, signatureValue);
            doc.setName(generatePrettyName(doc.getName(), document.getName()));
            return new SignedDocument(doc, token);
        } catch (DSSException e) {
            if (e.getMessage().contains("Cryptographic signature verification has failed"))
                throw new CryptographicSignatureVerificationException();

            throw e;
        }
    }

    private static String generatePrettyName(String newName, String originalName) {
        var lastDotIndex = originalName.lastIndexOf('.');
        var nameWithoutExtension = lastDotIndex == -1 ? originalName : originalName.substring(0, lastDotIndex);
        var extension = generatePrettyExtension(newName.substring(newName.lastIndexOf('.') + 1));

        return nameWithoutExtension + "_signed." + extension;
    }

    private static String generatePrettyExtension(String extension) {
        return switch (extension) {
            case "scs" -> "asics";
            case "sce" -> "asice";
            default -> extension;
        };
    }

    private static AbstractSignatureService getServiceForSignatureLevel(SignatureForm signatureForm, ASiCContainerType container, CertificateVerifier certificateVerifier) {
        return switch (signatureForm) {
            case XAdES -> container != null ? new ASiCWithXAdESService(certificateVerifier) : new XAdESService(certificateVerifier);
            case CAdES -> container != null ? new ASiCWithCAdESService(certificateVerifier) : new CAdESService(certificateVerifier);
            case PAdES -> new PAdESService(certificateVerifier);
            case JAdES -> new JAdESService(certificateVerifier);
            default -> throw new RuntimeException("Unsupported signature type: " + signatureForm);
        };
    }

    public DataToSignStructure buildDataToSign(String signingCertificate) throws CertificateException {
        var token = DSSUtils.parseCertificate(signingCertificate);
        var commonCertificateVerifier = new CommonCertificateVerifier();
        var signatureParameters = parameters.getSignatureParameters();
        var service = getServiceForSignatureLevel(parameters.getSignatureType(), parameters.getContainer(), commonCertificateVerifier);
        var signingTime = new Date();

        signatureParameters.setSigningCertificate(token);

        if (!signatureParameters.getSignatureLevel().toString().endsWith("_LEVEL_B"))
            service.setTspSource(parameters.getTspSource());

        var bLevelParameters = new BLevelParameters();
        bLevelParameters.setSigningDate(signingTime);
        signatureParameters.setBLevelParams(bLevelParameters);

        var dataToSign = Base64.getEncoder().encode(service.getDataToSign(document, signatureParameters).getBytes());

        return new DataToSignStructure(new String(dataToSign), signingTime.getTime(), signingCertificate);
    }

    public static FileDocument createDSSFileDocumentFromFile(File file) {
        var fileDocument = new FileDocument(file);

        if (isXDC(fileDocument.getMimeType()) || isXML(fileDocument.getMimeType()) && XDCValidator.isXDCContent(fileDocument))
            fileDocument.setMimeType(AutogramMimeType.XML_DATACONTAINER);

        return fileDocument;
    }

    private static SigningJob build(DSSDocument document, SigningParameters params) {
        if (params.shouldCreateXdc()) {
            var mimeType = document.getMimeType();
            if (!isXDC(mimeType) && !isAsice(mimeType)) {
                document = XDCBuilder.transform(params, document.getName(), EFormUtils.getXmlFromDocument(document));
                document.setMimeType(AutogramMimeType.XML_DATACONTAINER);
            }
        }

        return new SigningJob(document, params);
    }

    public static SigningJob buildWithParams(DSSDocument document, SigningParameters params) {
        return build(document, params);
    }

    public DSSDocument getDocument() {
        return document;
    }

    public SigningParameters getParameters() {
        return parameters;
    }
}
