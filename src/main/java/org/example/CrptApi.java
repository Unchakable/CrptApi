import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final Semaphore semaphore;
    private final BlockingQueue<LocalDateTime> requestTimestamps;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("requestLimit must be positive");
        }

        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit, true);
        this.requestTimestamps = new LinkedBlockingQueue<>(requestLimit);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        startCleanupThread();
    }

    public void createDocument(Document document, String signature) {
        try {
            acquirePermission();

            String jsonBody = objectMapper.writeValueAsString(document);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("Document created successfully: " + response.body());
            } else {
                System.err.println("Failed to create document. Status: " + response.statusCode() + ", Body: " + response.body());
            }

        } catch (JsonProcessingException e) {
            System.err.println("JSON serialization error: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Request interrupted: " + e.getMessage());
        } finally {
            semaphore.release();
        }
    }

    private void acquirePermission() throws InterruptedException {
        semaphore.acquire();
        LocalDateTime now = LocalDateTime.now();
        synchronized (requestTimestamps) {
            requestTimestamps.put(now);
        }
    }

    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    TimeUnit.MILLISECONDS.sleep(100);

                    LocalDateTime cutoffTime = LocalDateTime.now().minus(
                            Duration.ofMillis(timeUnit.toMillis(1))
                    );

                    synchronized (requestTimestamps) {
                        while (!requestTimestamps.isEmpty() &&
                                requestTimestamps.peek().isBefore(cutoffTime)) {
                            requestTimestamps.poll();
                            semaphore.release();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    public static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private LocalDateTime production_date;
        private String production_type;
        private List<Product> products;
        private LocalDateTime reg_date;
        private String reg_number;

        public Document() {}

        public Document(Description description, String doc_id, String doc_status,
                        String doc_type, boolean importRequest, String owner_inn,
                        String participant_inn, String producer_inn,
                        LocalDateTime production_date, String production_type,
                        List<Product> products, LocalDateTime reg_date, String reg_number) {
            this.description = description;
            this.doc_id = doc_id;
            this.doc_status = doc_status;
            this.doc_type = doc_type;
            this.importRequest = importRequest;
            this.owner_inn = owner_inn;
            this.participant_inn = participant_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.production_type = production_type;
            this.products = products;
            this.reg_date = reg_date;
            this.reg_number = reg_number;
        }

        public Description getDescription() { return description; }
        public void setDescription(Description description) { this.description = description; }

        public String getDoc_id() { return doc_id; }
        public void setDoc_id(String doc_id) { this.doc_id = doc_id; }

        public String getDoc_status() { return doc_status; }
        public void setDoc_status(String doc_status) { this.doc_status = doc_status; }

        public String getDoc_type() { return doc_type; }
        public void setDoc_type(String doc_type) { this.doc_type = doc_type; }

        public boolean isImportRequest() { return importRequest; }
        public void setImportRequest(boolean importRequest) { this.importRequest = importRequest; }

        public String getOwner_inn() { return owner_inn; }
        public void setOwner_inn(String owner_inn) { this.owner_inn = owner_inn; }

        public String getParticipant_inn() { return participant_inn; }
        public void setParticipant_inn(String participant_inn) { this.participant_inn = participant_inn; }

        public String getProducer_inn() { return producer_inn; }
        public void setProducer_inn(String producer_inn) { this.producer_inn = producer_inn; }

        public LocalDateTime getProduction_date() { return production_date; }
        public void setProduction_date(LocalDateTime production_date) { this.production_date = production_date; }

        public String getProduction_type() { return production_type; }
        public void setProduction_type(String production_type) { this.production_type = production_type; }

        public List<Product> getProducts() { return products; }
        public void setProducts(List<Product> products) { this.products = products; }

        public LocalDateTime getReg_date() { return reg_date; }
        public void setReg_date(LocalDateTime reg_date) { this.reg_date = reg_date; }

        public String getReg_number() { return reg_number; }
        public void setReg_number(String reg_number) { this.reg_number = reg_number; }
    }

    public static class Description {
        private String participantInn;

        public Description() {}

        public Description(String participantInn) {
            this.participantInn = participantInn;
        }
        public String getParticipantInn() { return participantInn; }
        public void setParticipantInn(String participantInn) { this.participantInn = participantInn; }
    }

    public static class Product {
        private String certificate_document;
        private LocalDateTime certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private LocalDateTime production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;

        public Product() {}

        public Product(String certificate_document, LocalDateTime certificate_document_date,
                       String certificate_document_number, String owner_inn,
                       String producer_inn, LocalDateTime production_date,
                       String tnved_code, String uit_code, String uitu_code) {
            this.certificate_document = certificate_document;
            this.certificate_document_date = certificate_document_date;
            this.certificate_document_number = certificate_document_number;
            this.owner_inn = owner_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.tnved_code = tnved_code;
            this.uit_code = uit_code;
            this.uitu_code = uitu_code;
        }

        public String getCertificate_document() { return certificate_document; }
        public void setCertificate_document(String certificate_document) { this.certificate_document = certificate_document; }

        public LocalDateTime getCertificate_document_date() { return certificate_document_date; }
        public void setCertificate_document_date(LocalDateTime certificate_document_date) { this.certificate_document_date = certificate_document_date; }

        public String getCertificate_document_number() { return certificate_document_number; }
        public void setCertificate_document_number(String certificate_document_number) { this.certificate_document_number = certificate_document_number; }

        public String getOwner_inn() { return owner_inn; }
        public void setOwner_inn(String owner_inn) { this.owner_inn = owner_inn; }

        public String getProducer_inn() { return producer_inn; }
        public void setProducer_inn(String producer_inn) { this.producer_inn = producer_inn; }

        public LocalDateTime getProduction_date() { return production_date; }
        public void setProduction_date(LocalDateTime production_date) { this.production_date = production_date; }

        public String getTnved_code() { return tnved_code; }
        public void setTnved_code(String tnved_code) { this.tnved_code = tnved_code; }

        public String getUit_code() { return uit_code; }
        public void setUit_code(String uit_code) { this.uit_code = uit_code; }

        public String getUitu_code() { return uitu_code; }
        public void setUitu_code(String uitu_code) { this.uitu_code = uitu_code; }
    }
}