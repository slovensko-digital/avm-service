package digital.slovensko.avm.core;

import digital.slovensko.avm.core.errors.TransformationException;
import digital.slovensko.avm.server.dto.DocumentResponse;
import digital.slovensko.avm.server.dto.OriginalSignRequestBody;
import digital.slovensko.avm.server.dto.SignResponse;
import digital.slovensko.avm.server.dto.SignerRecord;
import digital.slovensko.avm.server.errors.MalformedBodyException;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.pdfa.PDFAStructureValidator;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import eu.europa.esig.dss.validation.reports.Reports;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public class AVM {
    private final TSPSource tspSource;
    private final boolean plainXmlEnabled;

    public AVM(TSPSource tspSource, boolean plainXmlEnabled) {
        this.tspSource = tspSource;
        this.plainXmlEnabled = plainXmlEnabled;
    }

    public Reports checkAndValidateSignatures(DSSDocument document) {
        var reports = SignatureValidator.getInstance().getSignatureValidationReport(document);
        if (reports.getSimpleReport().getSignatureIdList().isEmpty())
            return null;

        return reports;
    }

    public boolean checkPDFACompliance(SigningJob job) {
        var result = new PDFAStructureValidator().validate(job.getDocument());
        return result.isCompliant();
    }

    public void initializeSignatureValidator(ScheduledExecutorService scheduledExecutorService, ExecutorService cachedExecutorService) {
        SignatureValidator.getInstance().initialize(cachedExecutorService);

        scheduledExecutorService.scheduleAtFixedRate(() -> SignatureValidator.getInstance().refresh(),
                480, 480, java.util.concurrent.TimeUnit.MINUTES);
    }

    public TSPSource getTspSource() {
        return tspSource;
    }

    public SignResponse signDocument(OriginalSignRequestBody originalSignRequestBody, DataToSignStructure dataToSignStructure, String signedData) throws CertificateException {
        var job = SigningJob.buildWithParams(originalSignRequestBody.getDocument(), originalSignRequestBody.getParameters(getTspSource(), plainXmlEnabled));
        var result = job.signDocument(dataToSignStructure, signedData);

        var signer = result.getCertificate().getSubject().getPrincipal().toString();
        var issuer = result.getCertificate().getIssuer().getPrincipal().toString();

        try {
            return new SignResponse(DocumentResponse.buildFormDSS(result.getDocument()), new SignerRecord(signer, issuer));
        } catch (IOException e) {
            throw new MalformedBodyException("", "");
        }
    }

    public DataToSignStructure getDataToSign(OriginalSignRequestBody originalSignRequestBody, String signingCertificate) throws CertificateException {
        var job = SigningJob.buildWithParams(originalSignRequestBody.getDocument(), originalSignRequestBody.getParameters(getTspSource(), plainXmlEnabled));
        return job.buildDataToSign(signingCertificate);
    }

    public DSSDocument getVisualization(OriginalSignRequestBody body) throws IOException, ParserConfigurationException, SAXException {
        var job = SigningJob.buildWithParams(body.getDocument(), body.getParameters(getTspSource(), plainXmlEnabled));
        var result = DocumentVisualizationBuilder.fromJob(job);

        if (result == null)
            throw new TransformationException("", "");

        return result;
    }
}
