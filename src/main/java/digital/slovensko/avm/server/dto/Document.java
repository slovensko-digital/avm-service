package digital.slovensko.avm.server.dto;

import eu.europa.esig.dss.model.InMemoryDocument;

import java.util.Base64;

public class Document {
    private String filename;
    private String content;

    public Document(String content) {
        this.content = content;
    }

    public Document(String filename, String content) {
        this.filename = filename;
        this.content = content;
    }

    public String getFilename() {
        return filename;
    }

    public String getContent() {
        return content;
    }

    public InMemoryDocument getDecodedContent() {
        return new InMemoryDocument(Base64.getDecoder().decode(content));
    }
}
