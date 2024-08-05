package digital.slovensko.avm.server.dto;

import digital.slovensko.avm.core.errors.DocumentNotSignedYetException;
import eu.europa.esig.dss.asic.cades.ASiCWithCAdESContainerExtractor;
import eu.europa.esig.dss.asic.common.AbstractASiCContainerExtractor;
import eu.europa.esig.dss.asic.xades.ASiCWithXAdESContainerExtractor;
import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.diagnostic.SignerDataWrapper;
import eu.europa.esig.dss.enumerations.MimeTypeEnum;
import eu.europa.esig.dss.enumerations.TimestampQualification;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.validation.AdvancedSignature;
import eu.europa.esig.dss.validation.DocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;

import javax.security.auth.x500.X500Principal;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.List;

public record ValidationResponseBody(String containerType, String signatureForm, List<Signature> signatures, List<SignedObject> signedObjects,
                                     List<UnsignedObject> unsignedObjects) {
    private static final SimpleDateFormat format = new SimpleDateFormat("yyy-MM-dd'T'HH:mm:ss Z");

    public static ValidationResponseBody build(Reports reports, DocumentValidator validator, DSSDocument document) throws DocumentNotSignedYetException {
        var simpleReport = reports.getSimpleReport();
        var diagnosticData = reports.getDiagnosticData();

        if (validator.getSignatures().isEmpty())
            throw new DocumentNotSignedYetException();

        var signatures = validator.getSignatures().stream().map((adeSignature) ->
            Signature.build(adeSignature, simpleReport, diagnosticData)
        ).toList();

        List<SignedObject> signedObjects = null;
        List<UnsignedObject> unsignedObjects = null;
        AbstractASiCContainerExtractor extractor = null;

        var signatureForm = simpleReport.getSignatureFormat(simpleReport.getFirstSignatureId()).getSignatureForm();
        String signatureFormString = null;
        if (signatureForm != null) {
            signatureFormString = signatureForm.name();
            switch (signatureForm) {
                case PAdES: {
                    signedObjects = diagnosticData.getAllSignerDocuments().stream().map((d) -> new SignedObject(
                            d.getId(),
                            MimeTypeEnum.PDF.getMimeTypeString(),
                            d.getReferencedName()
                    )).toList();
                    break;
                }
                case XAdES: {
                    extractor = new ASiCWithXAdESContainerExtractor(document);
                    break;
                }
                case CAdES: {
                    extractor = new ASiCWithCAdESContainerExtractor(document);
                    break;
                }
                default:
            }

            if (extractor != null) {
                var allObjects = extractor.extract().getSignedDocuments();
                signedObjects = getSignedObjects(allObjects, diagnosticData.getAllSignerDocuments());
                unsignedObjects = getUnsignedObjects(allObjects, diagnosticData.getAllSignerDocuments());

                if (signedObjects.isEmpty())
                    signedObjects = null;

                if (unsignedObjects.isEmpty())
                    unsignedObjects = null;
            }
        }

        var fileFormat = diagnosticData.getContainerType() != null ? diagnosticData.getContainerType().name() : null;

        return new ValidationResponseBody(fileFormat, signatureFormString, signatures, signedObjects, unsignedObjects);
    }

    private static List<SignedObject> getSignedObjects(List<DSSDocument> docs, List<SignerDataWrapper> signedObjects) {
        return signedObjects.stream().map((signedObject) -> {
            var r = docs.stream().filter((doc) -> doc.getName().equals(signedObject.getReferencedName())).toList();
            if (r.isEmpty())
                return null;

            return new SignedObject(
                    signedObject.getId(),
                    r.get(0).getMimeType().getMimeTypeString(),
                    signedObject.getReferencedName()
            );
        }).toList();
    }

    private static List<UnsignedObject> getUnsignedObjects(List<DSSDocument> docs, List<SignerDataWrapper> signedObjects) {
        return docs.stream().filter((o) -> signedObjects.stream().filter((s) -> o.getName().equals(s.getReferencedName())).toList().isEmpty()).map((generalObject) ->
                new UnsignedObject(
                        generalObject.getMimeType().getMimeTypeString(),
                        generalObject.getName()
                )
        ).toList();
    }

    private static byte[] getEncodedCertificateOrNull(X509Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (CertificateEncodingException e) {
            return null;
        }
    }

    record Signature(String validationResult, String level, String claimedSigningTime, String bestSigningTime,
                     CertificateInfo signingCertificate, boolean areQualifiedTimestamps, List<TimestampCertificateInfo> timestamps,
                     List<String> signedObjectsIds) {
        public static Signature build(AdvancedSignature signature, SimpleReport simpleReport, DiagnosticData diagnosticData) {
            var signatureId = signature.getId();
            var certificate = signature.getSigningCertificateToken().getCertificate();

            var timestamps = signature.getAllTimestamps().stream().map((timestamp) -> {
                var timestampId = timestamp.getDSSIdAsString();
                var timestampCertificate = timestamp.getCandidatesForSigningCertificate().getTheBestCandidate().getCertificateToken().getCertificate();

                return new TimestampCertificateInfo(
                        simpleReport.getTimestampQualification(timestampId).name(),
                        diagnosticData.getTimestampType(timestampId).name(),
                        timestampCertificate.getSubjectX500Principal().getName(X500Principal.RFC1779),
                        new String(Base64.getEncoder().encode(getEncodedCertificateOrNull(timestampCertificate))),
                        format.format(timestamp.getGenerationTime())
                );
            }).toList();

            return new Signature(
                    simpleReport.getIndication(signatureId).name(),
                    simpleReport.getSignatureFormat(signatureId).name(),
                    format.format(signature.getSigningTime()),
                    format.format(simpleReport.getBestSignatureTime(signatureId)),
                    new CertificateInfo(
                            simpleReport.getSignatureQualification(signatureId).name(),
                            certificate.getIssuerX500Principal().getName(X500Principal.RFC1779),
                            certificate.getSubjectX500Principal().getName(X500Principal.RFC1779),
                            new String(Base64.getEncoder().encode(getEncodedCertificateOrNull(certificate)))

                    ),
                    !signature.getSignatureTimestamps().isEmpty() && signature.getSignatureTimestamps().stream().allMatch((t -> t.isValid() && simpleReport.getTimestampQualification(t.getDSSIdAsString()).equals(TimestampQualification.QTSA))),
                    timestamps.isEmpty() ? null : timestamps,
                    diagnosticData.getSignerDocuments(signatureId).stream().map(SignerDataWrapper::getId).toList()
            );
        }
    }

    record CertificateInfo(String qualification, String issuerDN, String subjectDN, String certificateDer) {
    }

    record TimestampCertificateInfo(String qualification, String timestampType, String subjectDN, String certificateDer, String productionTime) {
    }

    record SignedObject(String id, String mimeType, String filename) {
    }

    record UnsignedObject(String mimeType, String filename) {
    }
}
