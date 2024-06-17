package digital.slovensko.avm.core;

import javax.xml.crypto.dsig.CanonicalizationMethod;

import digital.slovensko.avm.core.eforms.EFormAttributes;
import digital.slovensko.avm.core.errors.AutogramException;
import digital.slovensko.avm.core.errors.SigningParametersException;
import digital.slovensko.avm.core.errors.UnknownEformException;
import digital.slovensko.avm.util.AsicContainerUtils;
import digital.slovensko.avm.core.eforms.EFormResources;
import digital.slovensko.avm.core.eforms.EFormUtils;
import digital.slovensko.avm.core.eforms.XDCValidator;
import digital.slovensko.avm.core.eforms.dto.XsltParams;
import eu.europa.esig.dss.AbstractSignatureParameters;
import eu.europa.esig.dss.asic.cades.ASiCWithCAdESSignatureParameters;
import eu.europa.esig.dss.asic.xades.ASiCWithXAdESSignatureParameters;
import eu.europa.esig.dss.cades.CAdESSignatureParameters;
import eu.europa.esig.dss.enumerations.ASiCContainerType;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureForm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;

import static digital.slovensko.avm.core.AutogramMimeType.*;

public class SigningParameters {
    private final ASiCContainerType asicContainer;
    private final String containerXmlns;
    private final String schema;
    private final String transformation;
    private final SignatureLevel level;
    private final SignaturePackaging packaging;
    private final DigestAlgorithm digestAlgorithm;
    private final Boolean en319132;
    private final String infoCanonicalization;
    private final String propertiesCanonicalization;
    private final String keyInfoCanonicalization;
    private final String identifier;
    private final boolean checkPDFACompliance;
    private final boolean embedUsedSchemas;
    private final String xsdIdentifier;
    private final XsltParams xsltParams;
    private final TSPSource tspSource;

    private SigningParameters(SignatureLevel level, ASiCContainerType container,
            String containerXmlns, SignaturePackaging packaging, DigestAlgorithm digestAlgorithm,
            Boolean en319132, String infoCanonicalization, String propertiesCanonicalization,
            String keyInfoCanonicalization, String schema, String transformation, String identifier,
            boolean checkPDFACompliance, boolean embedUsedSchemas,
            String xsdIdentifier, XsltParams xsltParams, TSPSource tspSource) {
        this.level = level;
        this.asicContainer = container;
        this.containerXmlns = containerXmlns;
        this.packaging = packaging;
        this.digestAlgorithm = digestAlgorithm;
        this.en319132 = en319132;
        this.infoCanonicalization = infoCanonicalization;
        this.propertiesCanonicalization = propertiesCanonicalization;
        this.keyInfoCanonicalization = keyInfoCanonicalization;
        this.schema = schema;
        this.transformation = transformation;
        this.identifier = identifier;
        this.checkPDFACompliance = checkPDFACompliance;
        this.embedUsedSchemas = embedUsedSchemas;
        this.xsdIdentifier = xsdIdentifier;
        this.xsltParams = xsltParams;
        this.tspSource = tspSource;
    }

    public AbstractSignatureParameters getSignatureParameters() {
        return switch (getLevel().getSignatureForm()) {
            case XAdES -> getContainer() != null ? getASiCWithXAdESSignatureParameters() : getXAdESSignatureParameters();
            case CAdES -> getContainer() != null ? getASiCWithCAdESSignatureParameters() : getCAdESSignatureParameters();
            case PAdES -> getPAdESSignatureParameters();
            default -> throw new RuntimeException("Unsupported signature type: " + getLevel().getSignatureForm());
        };
    }

    private ASiCWithXAdESSignatureParameters getASiCWithXAdESSignatureParameters() {
        var parameters = new ASiCWithXAdESSignatureParameters();

        parameters.aSiC().setContainerType(getContainer());
        parameters.setSignatureLevel(getLevel());
        parameters.setDigestAlgorithm(getDigestAlgorithm());
        parameters.setSigningCertificateDigestMethod(getDigestAlgorithm());
        parameters.setSignedInfoCanonicalizationMethod(getInfoCanonicalization());
        parameters.setSignedPropertiesCanonicalizationMethod(getPropertiesCanonicalization());
        parameters.setKeyInfoCanonicalizationMethod(getKeyInfoCanonicalization());
        parameters.setEn319132(isEn319132());
        parameters.setAddX509SubjectName(true);

        return parameters;
    }

    private XAdESSignatureParameters getXAdESSignatureParameters() {
        var parameters = new XAdESSignatureParameters();

        parameters.setSignatureLevel(getLevel());
        parameters.setDigestAlgorithm(getDigestAlgorithm());
        parameters.setEn319132(isEn319132());
        parameters.setSignedInfoCanonicalizationMethod(getInfoCanonicalization());
        parameters.setSignedPropertiesCanonicalizationMethod(getPropertiesCanonicalization());
        parameters.setSignaturePackaging(getSignaturePackaging());
        parameters.setKeyInfoCanonicalizationMethod(getKeyInfoCanonicalization());
        parameters.setAddX509SubjectName(true);

        return parameters;
    }

    private CAdESSignatureParameters getCAdESSignatureParameters() {
        var parameters = new CAdESSignatureParameters();

        parameters.setSignatureLevel(getLevel());
        parameters.setDigestAlgorithm(getDigestAlgorithm());
        parameters.setSignaturePackaging(SignaturePackaging.ENVELOPING);
        parameters.setEn319122(isEn319132());

        return parameters;
    }

    private PAdESSignatureParameters getPAdESSignatureParameters() {
        var parameters = new PAdESSignatureParameters();

        parameters.setSignatureLevel(getLevel());
        parameters.setDigestAlgorithm(getDigestAlgorithm());
        parameters.setEn319122(isEn319132());

        return parameters;
    }

    private ASiCWithCAdESSignatureParameters getASiCWithCAdESSignatureParameters() {
        var parameters = new ASiCWithCAdESSignatureParameters();

        parameters.setSignatureLevel(getLevel());
        parameters.setDigestAlgorithm(getDigestAlgorithm());
        parameters.setEn319122(isEn319132());
        parameters.aSiC().setContainerType(getContainer());

        return parameters;
    }

    public SignatureForm getSignatureType() {
        return level.getSignatureForm();
    }

    public ASiCContainerType getContainer() {
        return asicContainer;
    }

    public String getContainerXmlns() {
        return containerXmlns;
    }

    public String getSchema() {
        return schema;
    }

    public String getTransformation() {
        return transformation;
    }

    public SignatureLevel getLevel() {
        return level != null ? level : SignatureLevel.XAdES_BASELINE_B;
    }

    public SignaturePackaging getSignaturePackaging() {
        return packaging != null ? packaging : SignaturePackaging.ENVELOPED;
    }

    public DigestAlgorithm getDigestAlgorithm() {
        return digestAlgorithm != null ? digestAlgorithm : DigestAlgorithm.SHA256;
    }

    public Boolean isEn319132() {
        return en319132 != null ? en319132 : false;
    }

    public String getInfoCanonicalization() {
        return infoCanonicalization != null ? infoCanonicalization
                : CanonicalizationMethod.INCLUSIVE;
    }

    public String getPropertiesCanonicalization() {
        return propertiesCanonicalization != null ? propertiesCanonicalization
                : CanonicalizationMethod.INCLUSIVE;
    }

    public String getKeyInfoCanonicalization() {
        return keyInfoCanonicalization != null ? keyInfoCanonicalization
                : CanonicalizationMethod.INCLUSIVE;
    }

    public static SigningParameters buildFromRequest(SignatureLevel level, ASiCContainerType container,
            String containerXmlns, SignaturePackaging packaging, DigestAlgorithm digestAlgorithm,
            Boolean en319132, String infoCanonicalization, String propertiesCanonicalization,
            String keyInfoCanonicalization, String schema, String transformation, String identifier,
            boolean checkPDFACompliance, boolean autoLoadEform, boolean embedUsedSchemas,
            String xsdIdentifier, String xsltIdentifier, String xsltLanguage, String xsltType, String xsltTarget,
            DSSDocument document, TSPSource tspSource, boolean plainXmlEnabled) throws AutogramException {

        return buildParameters(level, container, containerXmlns, packaging, digestAlgorithm, en319132,
                infoCanonicalization, propertiesCanonicalization, keyInfoCanonicalization, schema, transformation,
                identifier, checkPDFACompliance, autoLoadEform, embedUsedSchemas, xsdIdentifier,
                new XsltParams(xsltIdentifier, xsltLanguage, xsltType, xsltTarget, null),
                document, tspSource, plainXmlEnabled);
    }

    private static SigningParameters buildParameters(SignatureLevel level, ASiCContainerType container,
            String containerXmlns, SignaturePackaging packaging, DigestAlgorithm digestAlgorithm,
            Boolean en319132, String infoCanonicalization, String propertiesCanonicalization,
            String keyInfoCanonicalization, String schema, String transformation, String identifier,
            boolean checkPDFACompliance, boolean autoLoadEform, boolean embedUsedSchemas,
            String xsdIdentifier, XsltParams xsltParams, DSSDocument document, TSPSource tspSource,
            boolean plainXmlEnabled) throws AutogramException {

        if (level == null)
            throw new SigningParametersException("Nebol zadaný typ podpisu", "Typ/level podpisu je povinný atribút");

        if (document == null)
            throw new SigningParametersException("Dokument je prázdny", "Dokument poskytnutý na podpis je prázdny");

        if (document.getMimeType() == null)
            throw new SigningParametersException("Dokument nemá definovaný MIME type", "Dokument poskytnutý na podpis nemá definovaný MIME type");

        var extractedDocument = document;
        if (isAsice(document.getMimeType()))
            extractedDocument = AsicContainerUtils.getOriginalDocument(document);

        if (transformation == null && (isXML(extractedDocument.getMimeType()) || isXDC(extractedDocument.getMimeType())))
            autoLoadEform = true;

        if (isXML(extractedDocument.getMimeType()) && XDCValidator.isXDCContent(extractedDocument))
            extractedDocument.setMimeType(XML_DATACONTAINER);

        var extractedDocumentMimeType = extractedDocument.getMimeType();

        EFormAttributes eformAttributes = null;
        if (isXDC(extractedDocumentMimeType))
            eformAttributes = EFormResources.tryToLoadEFormAttributesFromEmbeddedXdc(extractedDocument);

        if (autoLoadEform && (isXML(extractedDocumentMimeType) || isXDC(extractedDocumentMimeType)) && (eformAttributes == null))
            eformAttributes = EFormResources.tryToLoadEFormAttributes(extractedDocument, propertiesCanonicalization, xsdIdentifier, xsltParams);

        if (eformAttributes != null) {
            schema = eformAttributes.schema();
            transformation = eformAttributes.transformation();
            identifier = eformAttributes.identifier();
            containerXmlns = eformAttributes.containerXmlns();
            container = eformAttributes.container();
            packaging = eformAttributes.packaging();
            xsdIdentifier = eformAttributes.xsdIdentifier();
            xsltParams = eformAttributes.xsltParams();
            embedUsedSchemas |= eformAttributes.embedUsedSchemas();
        }

        if (transformation != null)
            xsltParams = EFormUtils.fillXsltParams(transformation, identifier, xsltParams);

        if (containerXmlns != null && containerXmlns.contains("xmldatacontainer")) {

            if (schema == null)
                throw new SigningParametersException("Chýba XSD schéma", "XSD Schéma je povinný atribút pre XML Datacontainer");

            if (!embedUsedSchemas && xsdIdentifier == null)
                xsdIdentifier = EFormUtils.fillXsdIdentifier(identifier);

            if (transformation == null)
                throw new SigningParametersException("Chýba XSLT transformácia", "XSLT transformácia je povinný atribút pre XML Datacontainer");

            if (!embedUsedSchemas && identifier == null)
                throw new SigningParametersException("Chýba identifikátor", "Identifikátor je povinný atribút pre XML Datacontainer");

            if (digestAlgorithm == null)
                digestAlgorithm = DigestAlgorithm.SHA256;

            if (isXML(extractedDocumentMimeType) || isXDC(extractedDocumentMimeType))
                XDCValidator.validateXml(schema, transformation, extractedDocument, propertiesCanonicalization, digestAlgorithm, embedUsedSchemas);

            else
                throw new SigningParametersException("Nesprávny typ dokumentu", "Zadaný dokument nemožno podpísať ako elektronický formulár v XML Datacontaineri");
        }

        if (!plainXmlEnabled && (isXML(extractedDocumentMimeType) || isXDC(extractedDocumentMimeType)) && (transformation == null))
            throw new UnknownEformException();

        return new SigningParameters(level, container, containerXmlns, packaging, digestAlgorithm, en319132,
                infoCanonicalization, propertiesCanonicalization, keyInfoCanonicalization, schema, transformation,
                identifier, checkPDFACompliance, embedUsedSchemas, xsdIdentifier,
                xsltParams, tspSource);
    }

    public String getIdentifier() {
        return identifier;
    }

    public boolean getCheckPDFACompliance() {
        return checkPDFACompliance;
    }

    public String getXsltDestinationType() {
        return xsltParams.destinationType();
    }

    public String getXsdIdentifier() {
        return xsdIdentifier;
    }

    public boolean shouldCreateXdc() {
        return containerXmlns != null && containerXmlns.contains("xmldatacontainer");
    }

    public XsltParams getXsltParams() {
        return xsltParams;
    }

    public TSPSource getTspSource() {
        return tspSource;
    }

    public boolean shouldEmbedSchemas() {
        return embedUsedSchemas;
    }
}
