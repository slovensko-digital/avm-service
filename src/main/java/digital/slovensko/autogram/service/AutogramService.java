package digital.slovensko.autogram.service;

import digital.slovensko.autogram.core.*;
import digital.slovensko.autogram.core.dto.DataToSignStructure;
import digital.slovensko.autogram.core.dto.SignedDocument;
import digital.slovensko.autogram.core.util.DSSUtils;
import digital.slovensko.autogram.core.validation.SignatureValidator;
import digital.slovensko.autogram.core.visualization.DocumentVisualizationBuilder;
import digital.slovensko.autogram.core.errors.TransformationException;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.pdfa.PDFAStructureValidator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public class AutogramService implements SignatureExtender, SignatureComposer, VisualizationBuilder {
    private final Settings settings;

    public AutogramService(Settings settings) {
        this.settings = settings;
    }

    public boolean checkPDFACompliance(SigningJob job) {
        var result = new PDFAStructureValidator().validate(job.getDocument());
        return result.isCompliant();
    }

    public void initializeSignatureValidator(ScheduledExecutorService scheduledExecutorService, ExecutorService cachedExecutorService) {
        SignatureValidator.getInstance().initialize(cachedExecutorService, settings.getTrustedList());
        SignatureValidator.scheduleRefresh(scheduledExecutorService, 480, 480);
    }

    @Override
    public void buildSignedDocument(DSSDocument document, SigningParameters parameters, DataToSignStructure dataToSignStructure, String signedData, Responder responder) {
        var job = SigningJob.buildFromRequest(document, parameters, responder);
        job.signWithSignedDataAndRespond(dataToSignStructure, signedData, settings.getTspSource());
    }

    @Override
    public DataToSignStructure getDataToSign(DSSDocument document, SigningParameters parameters, String signingCertificate) {
        var job = SigningJob.buildFromRequest(document, parameters, null);
        return job.buildDataToSign(DSSUtils.parseCertificate(signingCertificate));
    }

    @Override
    public void extendDocument(DSSDocument document, BaselineLevel targetLevel, Responder responder) {
        var job = ExtendingJob.build(document, targetLevel, responder, SignatureValidator.getInstance(), settings);
        job.extendDocumentAndRespond(null);
    }

    @Override
    public void buildVisualizationAndRespond(DSSDocument document, SigningParameters parameters, Responder responder) {
        var job = SigningJob.buildFromRequest(document, parameters, responder);
        var result = DocumentVisualizationBuilder.fromJob(job);

        if (result == null) {
            responder.onError(new TransformationException("", ""));
            return;
        }

        var doc = result.getDocument();
        if (doc == null) {
            responder.onError(new TransformationException("", ""));
        }

        responder.onSuccess(new SignedDocument(doc, null));
    }

    @Override
    public boolean isPlainXmlEnabled() {
        return settings.isPlainXmlEnabled();
    }
}
