package digital.slovensko.avm.core;

public record DataToSignStructure(String dataToSign, Long signingTime, String signingCertificate) { }
