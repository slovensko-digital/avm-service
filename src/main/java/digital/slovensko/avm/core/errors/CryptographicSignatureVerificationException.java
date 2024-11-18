package digital.slovensko.avm.core.errors;

public class CryptographicSignatureVerificationException extends AutogramException {
    public CryptographicSignatureVerificationException() {
        super("Cryptographic signature verification failed", "Cryptographic signature verification failed", "Created signature is cryptographically invalid.");
    }
}
