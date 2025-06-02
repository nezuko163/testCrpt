import com.nezuko.CrptApi;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrptApiTest {
    private static HttpServer server;
    private static String baseUrl;

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port + "/api/v3";

        server.createContext("/api/v3/lk/documents/create", (HttpExchange exchange) -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            InputStream is = exchange.getRequestBody();
            byte[] requestBytes = is.readAllBytes();
            String requestBody = new String(requestBytes, StandardCharsets.UTF_8);
            is.close();

            assertTrue(requestBody.contains("\"type\":\"LP_INTRODUCE_GOODS\""),
                    "Ожидалось, что в теле запроса будет поле \"type\":\"LP_INTRODUCE_GOODS\"");

            String responseJson = "{\"value\":\"test‑doc‑id\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] respBytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respBytes);
            }
        });

        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testCreateIntroduceGoodsDocument() throws Exception {
        CrptApi api = new CrptApi(5, TimeUnit.SECONDS, "dummy-token", baseUrl);

        CrptApi.IntroduceGoodsDocument doc = CrptApi.IntroduceGoodsDocument.builder()
                .description(new CrptApi.IntroduceGoodsDocument.Description("1111111111"))
                .docId("doc‑1")
                .docStatus("DRAFT")
                .docType("LP_INTRODUCE_GOODS")
                .importRequest(false)
                .ownerInn("1111111111")
                .participantInn("1111111111")
                .producerInn("1111111111")
                .productionDate("2025-06-01")
                .productionType("OWN_PRODUCTION")
                .products(Collections.emptyList())
                .regDate("2025-06-01")
                .regNumber("reg‑1")
                .build();

        String dummySignature = "dummy‑signature";

        HttpResponse<String> response = api.createDocument(doc, dummySignature, CrptApi.DocumentFormat.LP_INTRODUCE_GOODS, "asd", "asd");

        assertEquals(200, response.statusCode(), "Ожидался HTTP 200");
        assertTrue(response.body().contains("test‑doc‑id"),
                "Ожидалось, что тело ответа содержит \"test‑doc‑id\"");

        api.stop();
    }
}