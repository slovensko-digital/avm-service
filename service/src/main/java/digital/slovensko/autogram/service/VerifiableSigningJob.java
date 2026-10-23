package digital.slovensko.autogram.service;

import digital.slovensko.autogram.core.BaselineLevel;
import digital.slovensko.autogram.core.SigningJob;
import digital.slovensko.autogram.core.dto.DataToSignStructure;
import digital.slovensko.autogram.core.dto.SignedDocument;
import digital.slovensko.autogram.core.errors.*;
import digital.slovensko.autogram.core.util.DSSUtils;
import eu.europa.esig.dss.model.BLevelParameters;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.signature.AbstractSignatureParameters;
import eu.europa.esig.dss.signature.AbstractSignatureService;
import eu.europa.esig.dss.spi.exception.DSSExternalResourceException;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;

import java.util.Base64;
import java.util.Date;

/**
 * Builds DataToSign and signed document of a {@link SigningJob} in a way, that the client can verify DataToSign
 * against the document visualized from {@link VerifiableVisualizationBuilder}:
 * <ul>
 *     <li>signing time can be set by the client, so PAdES signature uses the same signing time as the PDF prepared
 *     for verifiable visualization,</li>
 *     <li>PDF prepared for PAdES signature does not depend on the signing certificate nor on the signature level.</li>
 * </ul>
 */
@SuppressWarnings("unchecked")
public class VerifiableSigningJob {
    /**
     * Space reserved for CMS in PDF prepared for PAdES signature. It has to be big enough for signature timestamp
     * (PAdES BASELINE T) and it has to be the same for all levels, because signature level can be changed after the
     * prepared PDF was visualized.
     */
    public static final int PADES_CONTENT_SIZE = 9472 * 2;

    private final SigningJob job;

    public VerifiableSigningJob(SigningJob job) {
        this.job = job;
    }

    public DataToSignStructure buildDataToSign(CertificateToken signingCertificate, Date signingTime) {
        var signatureParameters = buildSignatureParameters(signingCertificate, signingTime);
        var dataToSign = buildService().getDataToSign(job.getDocument(), signatureParameters);

        return new DataToSignStructure(Base64.getEncoder().encodeToString(dataToSign.getBytes()), signingTime.getTime(),
                DSSUtils.encodeCertificate(signingCertificate));
    }

    public SignedDocument buildSignedDocument(DataToSignStructure dataToSignStructure, String signedData, TSPSource tspSource) {
        var token = DSSUtils.parseCertificate(dataToSignStructure.signingCertificate());
        var signatureValue = new SignatureValue(token.getSignatureAlgorithm(), Base64.getDecoder().decode(signedData));
        var signatureParameters = buildSignatureParameters(token, new Date(dataToSignStructure.signingTime()));
        signatureParameters.setCertificateChain(token);

        var service = buildService();
        if (!BaselineLevel.build(signatureParameters.getSignatureLevel()).equals(BaselineLevel.B))
            service.setTspSource(tspSource);

        try {
            var dataToSign = service.getDataToSign(job.getDocument(), signatureParameters);
            if (!Base64.getEncoder().encodeToString(dataToSign.getBytes()).equals(dataToSignStructure.dataToSign()))
                throw new DataToSignMismatchException();

            if (!service.isValidSignatureValue(dataToSign, signatureValue, token))
                throw new CryptographicSignatureVerificationException();

            DSSDocument doc;
            try {
                doc = service.signDocument(job.getDocument(), signatureParameters, signatureValue);
                doc.setName(DSSUtils.generatePrettyName(doc.getName(), job.getDocument().getName()));
            } catch (DSSException e) {
                if (e.getMessage().contains("Cryptographic signature verification has failed"))
                    throw new CryptographicSignatureVerificationException();

                throw e;
            }

            return new SignedDocument(doc, token);

        } catch (IllegalArgumentException e) {
            throw AutogramException.createFromIllegalArgumentException(e);

        } catch (DSSException e) {
            for (Throwable cause = e; cause != null && cause.getCause() != cause; cause = cause.getCause()) {
                if (cause.getMessage() == null)
                    continue;

                if (cause instanceof DSSExternalResourceException) {
                    throw new TsaServerMisconfiguredException("Nastavený TSA server odmietol pridať časovú pečiatku. Skontrolujte nastavenia TSA servera.", cause);
                } else if (cause instanceof NullPointerException && cause.getMessage().contains("Host name")) {
                    throw new TsaServerMisconfiguredException("Nie je nastavená žiadna adresa TSA servera. Skontrolujte nastavenia TSA servera.", cause);
                }
            }

            throw new UnrecognizedException(e);
        }
    }

    /**
     * Prepares PDF for PAdES signature - the document with signature dictionary and empty space for CMS, which is
     * digested by DataToSign of the same signing time.
     */
    public DSSDocument buildPdfToBeSigned(Date signingTime) {
        var signatureParameters = buildSignatureParameters(null, signingTime);
        if (!(signatureParameters instanceof PAdESSignatureParameters padesSignatureParameters))
            throw new IllegalStateException("PDF can be prepared only for PAdES signature");

        new PdfPreparingPAdESService().prepare(job.getDocument(), padesSignatureParameters);

        return padesSignatureParameters.getPdfSignatureCache().getToBeSignedDocument();
    }

    private AbstractSignatureParameters<?> buildSignatureParameters(CertificateToken signingCertificate, Date signingTime) {
        var parameters = job.getParameters();
        var signatureParameters = parameters.getSignatureParameters();
        parameters.applySignatureReference(job.getDocument(), signatureParameters);

        if (signingCertificate != null)
            signatureParameters.setSigningCertificate(signingCertificate);

        var bLevelParameters = signatureParameters.bLevel();
        if (bLevelParameters == null)
            bLevelParameters = new BLevelParameters();

        bLevelParameters.setSigningDate(signingTime);
        signatureParameters.setBLevelParams(bLevelParameters);

        if (signatureParameters instanceof PAdESSignatureParameters padesSignatureParameters) {
            padesSignatureParameters.setContentSize(PADES_CONTENT_SIZE);
            // PDF document ID is derived from the deterministic id, which contains signing certificate by default,
            // but the PDF is prepared for verifiable visualization before the signing certificate is known
            padesSignatureParameters.getContext().setDeterministicId(
                    eu.europa.esig.dss.spi.DSSUtils.getDeterministicId(signingTime, null));
        }

        return signatureParameters;
    }

    @SuppressWarnings("rawtypes")
    private AbstractSignatureService buildService() {
        var parameters = job.getParameters();
        return DSSUtils.getServiceForSignatureLevel(parameters.getSignatureType(), parameters.getContainer(),
                new CommonCertificateVerifier());
    }

    private static class PdfPreparingPAdESService extends PAdESService {
        PdfPreparingPAdESService() {
            super(new CommonCertificateVerifier());
        }

        void prepare(DSSDocument document, PAdESSignatureParameters parameters) {
            // computing the digest caches the prepared PDF in parameters' PdfSignatureCache
            computeDocumentDigest(document, parameters);
        }
    }
}
