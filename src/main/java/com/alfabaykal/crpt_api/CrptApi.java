package com.alfabaykal.crpt_api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final ChronoUnit limitRate;
    private final int limit;
    private final Semaphore semaphore;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final URI API_URL;
    private final ScheduledExecutorService scheduler;

    static {
        try {
            API_URL = new URI("https://ismp.crpt.ru/api/v3/lk/documents/create");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * @param limitRate - частота обновления счетчика семафора
     * @param limit     - максимальное количество одновременных запросов
     */
    public CrptApi(ChronoUnit limitRate, int limit) {
        this.limitRate = limitRate;
        this.limit = limit;

        semaphore = new Semaphore(limit, true);
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .executor(Executors.newFixedThreadPool(limit))
                .build();
        this.scheduler = Executors.newScheduledThreadPool(1);

        runSemaphoreReleaser();
    }

    /**
     * Создание документа
     *
     * @param document  - документ
     * @param signature - подпись
     */
    public void createDocument(DocumentDto document, String signature) {
        Objects.requireNonNull(document, "Document must not be null");
        Objects.requireNonNull(signature, "Signature must not be null");

        try {
            semaphore.acquire();
            doRequest(document, signature);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            semaphore.release();
        }
    }

    /**
     * Выполнение запроса
     *
     * @param document  - документ
     * @param signature - подпись
     */
    private void doRequest(DocumentDto document, String signature) {
        HttpResponse<String> response;

        try {
            HttpRequest httpRequest = buildRequest(document, signature);
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiCallException(e);
        } catch (Exception e) {
            throw new ApiCallException(String.format("Unexpected error during call API: %s", e.getMessage()), e);
        }

        if (response.statusCode() != HttpURLConnection.HTTP_OK) {
            throw new ApiCallException(String.format("API return bad status code: %d", response.statusCode()));
        }
    }

    /**
     * Построение запроса
     *
     * @param document  - документ
     * @param signature - подпись
     * @return - запрос
     */
    private HttpRequest buildRequest(DocumentDto document, String signature) throws JsonProcessingException {
        CreateDocumentRequest createDocumentRequest = CreateDocumentRequest.from(document, signature);
        return HttpRequest.newBuilder()
                .uri(API_URL)
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(createDocumentRequest)))
                .build();
    }

    /**
     * Запуск семафора
     */
    private void runSemaphoreReleaser() {
        scheduler.scheduleAtFixedRate(() -> {
            semaphore.release(limit - semaphore.availablePermits());
        }, limitRate.getDuration().toMillis(), limitRate.getDuration().toMillis(), TimeUnit.MILLISECONDS);
    }

    public static class ApiCallException extends RuntimeException {
        public ApiCallException(String message, Throwable cause) {
            super(message, cause);
        }

        public ApiCallException(Throwable cause) {
            super(cause);
        }

        public ApiCallException(String message) {
            super(message);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Builder
    public static class DocumentDto {
        // Поля документа
    }

    public static class CreateDocumentRequest {
        // Поля запроса на создание документа

        /**
         * Создание запроса на создание документа
         *
         * @param document  - документ
         * @param signature - подпись
         * @return - запрос на создание документа
         */
        public static CreateDocumentRequest from(DocumentDto document, String signature) {
            var request = new CreateDocumentRequest();
            // Заполнение полей запроса
            return request;
        }
    }
}

