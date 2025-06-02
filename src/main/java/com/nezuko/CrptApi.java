package com.nezuko;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class CrptApi {
    private final int requestLimit;

    // к апи нельзя обратиться без токена, нужно заранее его полуить
    private final String apiKey;
    // может быть:
    // 1) https://ismp.crpt.ru/api/v3 - для прода
    // 2) https://markirovka.demo.crpt.tech/ - для разработки и тестов
    private final String baseHost;

    private final HttpClient httpClient;


    // rate limiter
    private final Semaphore semaphore;
    private final ScheduledExecutorService refillScheduler;


    private final ObjectMapper objectMapper;

    public CrptApi(int requestLimit, TimeUnit timeUnit, String apiKey) {
        this(requestLimit, timeUnit, apiKey, "https://markirovka.demo.crpt.tech/api/v3");
    }

    public CrptApi(int requestLimit, TimeUnit timeUnit, String apiKey, String baseHost) {
        this.requestLimit = requestLimit;
        this.apiKey = apiKey.startsWith("Bearer ") ? apiKey : "Bearer " + apiKey;
        this.baseHost = baseHost;

        objectMapper = new ObjectMapper();
        semaphore = new Semaphore(requestLimit);
        refillScheduler = Executors.newSingleThreadScheduledExecutor(
                thread -> {
                    Thread t = new Thread(thread, "refillScheduler");
                    t.setDaemon(true);
                    return t;
                }
        );

        // восстанавилваем количество потребляемых раз в единицу времени
        refillScheduler.scheduleAtFixedRate(() -> {
            semaphore.drainPermits();
            semaphore.release(requestLimit);
        }, 1, 1, timeUnit);


        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

    }


    public HttpResponse<String> createDocument(
            IntroduceGoodsDocument document,
            String signature,
            DocumentFormat documentFormat,
            String productGroup,
            String type
    ) throws InterruptedException, IOException {
        semaphore.acquire();
        byte[] docJsonBytes = objectMapper.writeValueAsBytes(document);
        String docBase64 = Base64.getEncoder().encodeToString(docJsonBytes);

        CreateDocumentRequest wrapper = CreateDocumentRequest.builder()
                .documentFormat(documentFormat.name())
                .productDocument(docBase64)
                .productGroup(productGroup)
                .signature(signature)
                .type(type)
                .build();

        String data = objectMapper.writeValueAsString(wrapper);

        return httpClient.send(createRequest(data), HttpResponse.BodyHandlers.ofString());
    }


    // вызвать после окончания работы с апи, чтобы остановить демон потоки
    public void stop() {
        refillScheduler.shutdownNow();
        semaphore.release(requestLimit);
    }

    private HttpRequest createRequest(String data) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseHost + "/lk/documents/create"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(data))
                .build();
    }


    @Data
    @Builder
    private static class CreateDocumentRequest {
        @JsonProperty("document_format")
        private final String documentFormat;

        @JsonProperty("product_document")
        private final String productDocument;

        @JsonProperty("product_group")
        private final String productGroup;

        @JsonProperty("signature")
        private final String signature;

        @JsonProperty("type")
        private final String type;
    }

    public enum DocumentFormat {
        LP_INTRODUCE_GOODS,
        LP_INTRODUCE_GOODS_XML,
        LP_INTRODUCE_GOODS_CSV
    }

    @Data
    @Builder
    public static class IntroduceGoodsDocument {
        @JsonProperty("description")
        private final Description description;

        @JsonProperty("doc_id")
        private final String docId;

        @JsonProperty("doc_status")
        private final String docStatus;

        @JsonProperty("doc_type")
        private final String docType;

        @JsonProperty("importRequest")
        private final Boolean importRequest;

        @JsonProperty("owner_inn")
        private final String ownerInn;

        @JsonProperty("participant_inn")
        private final String participantInn;

        @JsonProperty("producer_inn")
        private final String producerInn;

        @JsonProperty("production_date")
        private final String productionDate;

        @JsonProperty("production_type")
        private final String productionType;

        @JsonProperty("products")
        private final java.util.List<Product> products;

        @JsonProperty("reg_date")
        private final String regDate;

        @JsonProperty("reg_number")
        private final String regNumber;

        @Data
        public static class Description {
            @JsonProperty("participantInn")
            private final String participantInn;
        }

        @Data
        @Builder
        public static class Product {
            @JsonProperty("certificate_document")
            private final String certificateDocument;

            @JsonProperty("certificate_document_date")
            private final String certificateDocumentDate;

            @JsonProperty("certificate_document_number")
            private final String certificateDocumentNumber;

            @JsonProperty("owner_inn")
            private final String ownerInn;

            @JsonProperty("producer_inn")
            private final String producerInn;

            @JsonProperty("production_date")
            private final String productionDate;

            @JsonProperty("tnved_code")
            private final String tnvedCode;

            @JsonProperty("uit_code")
            private final String uitCode;

            @JsonProperty("uitu_code")
            private final String uituCode;
        }
    }
}


