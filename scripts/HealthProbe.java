import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.regex.Pattern;

class HealthProbe {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        String email = escape(System.getenv("ADMIN_EMAIL"));
        String password = escape(System.getenv("ADMIN_PASSWORD"));
        var login = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/auth/login"))
            .timeout(Duration.ofSeconds(3)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).build(), HttpResponse.BodyHandlers.ofString());
        var matcher = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"").matcher(login.body());
        if (login.statusCode() != 200 || !matcher.find()) System.exit(1);
        var health = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/actuator/health"))
            .timeout(Duration.ofSeconds(3)).header("Authorization", "Bearer " + matcher.group(1)).build(), HttpResponse.BodyHandlers.ofString());
        if (health.statusCode() != 200 || !health.body().contains("\"UP\"")) System.exit(1);
    }
    private static String escape(String value) {
        if (value == null) throw new IllegalArgumentException("Admin credentials are required for health checks");
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
