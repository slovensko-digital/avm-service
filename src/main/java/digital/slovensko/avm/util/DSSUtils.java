package digital.slovensko.avm.util;

import eu.europa.esig.dss.asic.cades.validation.ASiCContainerWithCAdESValidatorFactory;
import eu.europa.esig.dss.asic.xades.validation.ASiCContainerWithXAdESValidatorFactory;
import eu.europa.esig.dss.cades.validation.CMSDocumentValidatorFactory;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.validation.PDFDocumentValidatorFactory;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.xades.validation.XMLDocumentValidatorFactory;
import sun.security.x509.X509CertImpl;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.security.cert.CertificateException;
import java.util.Base64;

public class DSSUtils {
    public static String parseCN(String rfc2253) {
        try {
            var ldapName = new LdapName(rfc2253);
            for (Rdn rdn : ldapName.getRdns()) {
                if (rdn.getType().equalsIgnoreCase("CN")) {
                    return rdn.getValue().toString();
                }
            }
        } catch (InvalidNameException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static CertificateToken parseCertificate(String certString) throws CertificateException {
        return new CertificateToken(new X509CertImpl(Base64.getDecoder().decode(certString)));
    }

    public static SignedDocumentValidator createDocumentValidator(DSSDocument document) {
        if (new PDFDocumentValidatorFactory().isSupported(document))
            return new PDFDocumentValidatorFactory().create(document);

        if (new XMLDocumentValidatorFactory().isSupported(document))
            return new XMLDocumentValidatorFactory().create(document);

        if (new ASiCContainerWithXAdESValidatorFactory().isSupported(document))
            return new ASiCContainerWithXAdESValidatorFactory().create(document);

        if (new ASiCContainerWithCAdESValidatorFactory().isSupported(document))
            return new ASiCContainerWithCAdESValidatorFactory().create(document);

        if (new CMSDocumentValidatorFactory().isSupported(document))
            return new CMSDocumentValidatorFactory().create(document);

        return null;
    }
}
