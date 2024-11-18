package digital.slovensko.avm.server.dto;

public class InfoResponse {
    private final String status;

    public InfoResponse(String status) {
        this.status = status;
    }

    public static String getStatus() {
        return "READY"; // TODO: check if server is ready
    }
}
