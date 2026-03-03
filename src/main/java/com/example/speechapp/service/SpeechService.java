package com.example.speechapp.service;

import com.example.speechapp.config.AzureSpeechConfig;
import com.example.speechapp.model.TranscriptionJob;
import com.example.speechapp.model.TranscriptionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Binod Suman
 *
 * Purpose of SpeechService.java
 * SpeechService.java is the core service class that contains the main business logic for speech
 * transcription with speaker diarization. It orchestrates the entire transcription process, from job
 * creation to result parsing, and handles asynchronous processing with Azure Speech Services.
 *
 * Detailed Explanation
 * Primary Purpose
 * The service acts as the brain of the application, responsible for:
 *
 * Managing the complete transcription workflow
 *
 * Handling asynchronous job processing
 * Communicating with Azure Speech REST API
 * Polling for job completion
 * Parsing and transforming results
 * Coordinating with BlobStorage and Email services
 * Providing detailed progress logging
 */
@Service
public class SpeechService {

    private static final Logger logger = LoggerFactory.getLogger(SpeechService.class);

    @Autowired
    private AzureSpeechConfig speechConfig;

    @Autowired
    private BlobStorageService blobStorageService;

    @Autowired
    private EmailService emailService;

    @Value("${job.polling.max-attempts:120}")
    private int maxPollingAttempts;

    @Value("${job.status.update-interval:5}")
    private int statusUpdateInterval;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Map<String, TranscriptionJob> jobStore = new ConcurrentHashMap<>();

    /**
     * Start a new transcription job or return existing transcript
     */
    public TranscriptionJob startTranscriptionJob(MultipartFile file, String email) throws IOException {
        String originalFilename = file.getOriginalFilename();

        logger.info("=".repeat(80));
        logger.info("PROCESSING FILE: {}", originalFilename);
        logger.info("=".repeat(80));

        // Check if file already exists and has transcript
        if (blobStorageService.fileExists(originalFilename) &&
                blobStorageService.transcriptExists(originalFilename)) {

            logger.info("File already processed. Retrieving existing transcript...");
            String transcriptJson = blobStorageService.getTranscriptContent(originalFilename);

            if (transcriptJson != null) {
                TranscriptionResult result = objectMapper.readValue(transcriptJson, TranscriptionResult.class);
                List<SpeakerSegment> segments = parseTranscriptionResult(result);

                // Create completed job with existing data
                TranscriptionJob job = new TranscriptionJob();
                job.setJobId("EXISTING-" + UUID.randomUUID().toString());
                job.setFileName(originalFilename);
                job.setFileSize(file.getSize());
                job.setEmail(email);
                job.setStatus(TranscriptionJob.JobStatus.COMPLETED);
                job.setProgress(100);
                job.setMessage("Using existing transcript from blob storage");
                job.setCreatedAt(LocalDateTime.now());
                job.setCompletedAt(LocalDateTime.now());
                job.setSegments(segments);

                logger.info("✓ Retrieved existing transcript with {} segments", segments.size());
                logger.info("=".repeat(80));

                jobStore.put(job.getJobId(), job);
                return job;
            }
        }

        // Start new transcription job
        logger.info("No existing transcript found. Starting new transcription job...");

        TranscriptionJob job = new TranscriptionJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setFileName(originalFilename);
        job.setFileSize(file.getSize());
        job.setEmail(email);
        job.setStatus(TranscriptionJob.JobStatus.UPLOADING);
        job.setMessage("Starting transcription job...");
        job.setProgress(0);

        jobStore.put(job.getJobId(), job);

        logger.info("Job ID assigned: {}", job.getJobId());

        // Start async processing
        processJobAsync(job, file);

        return job;
    }

    @Async
    protected void processJobAsync(TranscriptionJob job, MultipartFile file) {
        String sasUrl = null;
        String originalFilename = file.getOriginalFilename();

        try {
            // Step 1: Upload to Blob Storage
            updateJobProgress(job, TranscriptionJob.JobStatus.UPLOADING, 10,
                    "Uploading to Azure Blob Storage...");

            logger.info("Step 1/5: Uploading file to Blob Storage");
            sasUrl = blobStorageService.uploadFileAndGenerateSas(file, originalFilename);
            job.setBlobSasUrl(sasUrl);
            logger.info("✓ File uploaded successfully");

            // Step 2: Create Transcription Job
            updateJobProgress(job, TranscriptionJob.JobStatus.PROCESSING, 25,
                    "Creating Azure Speech transcription job...");

            logger.info("Step 2/5: Creating Azure Speech transcription job");
            String transcriptionUrl = createTranscription(sasUrl, job.getJobId());
            job.setAzureTranscriptionUrl(transcriptionUrl);
            logger.info("✓ Transcription job created");

            // Step 3: Poll for Completion
            updateJobProgress(job, TranscriptionJob.JobStatus.PROCESSING, 40,
                    "Processing audio with speaker diarization...");

            logger.info("Step 3/5: Polling for transcription completion");
            TranscriptionResult result = pollForTranscriptionResult(transcriptionUrl, job);

            // Step 4: Parse Results
            updateJobProgress(job, TranscriptionJob.JobStatus.PROCESSING, 90,
                    "Parsing transcription results...");

            logger.info("Step 4/5: Parsing transcription results");
            List<SpeakerSegment> segments = parseTranscriptionResult(result);
            job.setSegments(segments);
            logger.info("✓ Parsed {} speaker segments", segments.size());

            // Step 5: Save Transcript to Blob
            updateJobProgress(job, TranscriptionJob.JobStatus.PROCESSING, 95,
                    "Saving transcript to blob storage...");

            logger.info("Step 5/5: Saving transcript to blob storage");
            String transcriptJson = objectMapper.writeValueAsString(result);
            String transcriptUrl = blobStorageService.saveTranscriptionResult(originalFilename, transcriptJson);
            logger.info("✓ Transcript saved at: {}", transcriptUrl);

            // Complete Job
            job.setStatus(TranscriptionJob.JobStatus.COMPLETED);
            job.setProgress(100);
            job.setCompletedAt(LocalDateTime.now());
            job.setMessage("Transcription completed successfully!");

            logger.info("=".repeat(80));
            logger.info("JOB COMPLETED SUCCESSFULLY");
            logger.info("Job ID: {}", job.getJobId());
            logger.info("Total Segments: {}", segments.size());
            logger.info("Total Speakers: {}", job.getTotalSpeakers());
            logger.info("=".repeat(80));

            // Send email notification if provided
            if (job.getEmail() != null && !job.getEmail().isEmpty()) {
                emailService.sendCompletionNotification(job);
            }

        } catch (Exception e) {
            logger.error("!" .repeat(80));
            logger.error("JOB FAILED: {}", job.getJobId());
            logger.error("Error: {}", e.getMessage());
            logger.error("!" .repeat(80));

            job.setStatus(TranscriptionJob.JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setMessage("Transcription failed: " + e.getMessage());

            if (job.getEmail() != null && !job.getEmail().isEmpty()) {
                emailService.sendFailureNotification(job, e.getMessage());
            }
        } finally {
            // Clean up temporary blob (keep the file but delete the temporary one)
            if (sasUrl != null && !sasUrl.contains("?")) {
                try {
                    blobStorageService.deleteFile(sasUrl);
                } catch (Exception e) {
                    logger.error("Error cleaning up blob: {}", e.getMessage());
                }
            }
        }
    }

    private void updateJobProgress(TranscriptionJob job, TranscriptionJob.JobStatus status,
                                   int progress, String message) {
        job.setStatus(status);
        job.setProgress(progress);
        job.setMessage(message);
        logger.info("[{}%] {}", progress, message);
    }

    private String createTranscription(String audioUrlWithSas, String jobId) throws IOException {
        String endpoint = "https://" + speechConfig.getRegion() +
                ".api.cognitive.microsoft.com/speechtotext/v3.1/transcriptions";

        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Ocp-Apim-Subscription-Key", speechConfig.getKey());
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);

            Map<String, Object> transcriptionDefinition = new HashMap<>();
            transcriptionDefinition.put("contentUrls", Collections.singletonList(audioUrlWithSas));
            transcriptionDefinition.put("locale", speechConfig.getLanguage());
            transcriptionDefinition.put("displayName", "SpeakerDiarization_" + jobId + "_" +
                    new Date().toString());

            Map<String, Object> properties = new HashMap<>();
            properties.put("diarizationEnabled", true);
            properties.put("channels", new int[]{0});
            properties.put("punctuationMode", "DictatedAndAutomatic");
            properties.put("profanityFilterMode", "Masked");
            properties.put("wordLevelTimestampsEnabled", true);

            transcriptionDefinition.put("properties", properties);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = objectMapper.writeValueAsBytes(transcriptionDefinition);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == 201) {
                return connection.getHeaderField("location");
            } else {
                String errorResponse = readErrorResponse(connection);
                throw new RuntimeException("Failed to create transcription. Code: " +
                        responseCode + ", Error: " + errorResponse);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private TranscriptionResult pollForTranscriptionResult(String transcriptionUrl, TranscriptionJob job)
            throws IOException, InterruptedException {

        int attempt = 0;

        while (attempt < maxPollingAttempts) {
            attempt++;
            HttpURLConnection connection = null;

            try {
                URL url = new URL(transcriptionUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Ocp-Apim-Subscription-Key", speechConfig.getKey());

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    Map<String, Object> response = objectMapper.readValue(
                            connection.getInputStream(), Map.class);
                    String status = (String) response.get("status");

                    // Update progress
                    int progressBase = 40;
                    int progressRange = 50;
                    int estimatedProgress = progressBase + (attempt * progressRange / maxPollingAttempts);
                    job.setProgress(Math.min(estimatedProgress, 90));

                    if (attempt % 10 == 0) {
                        logger.info("   Polling attempt {}/{}: Status = {}",
                                attempt, maxPollingAttempts, status);
                    }

                    if ("Succeeded".equals(status)) {
                        logger.info("✓ Azure job completed after {} attempts", attempt);
                        Map<String, Object> links = (Map<String, Object>) response.get("links");
                        String filesUrl = (String) links.get("files");
                        return downloadTranscriptionFiles(filesUrl);

                    } else if ("Failed".equals(status)) {
                        throw new RuntimeException("Transcription failed");
                    }
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            TimeUnit.SECONDS.sleep(statusUpdateInterval);
        }

        throw new RuntimeException("Transcription timed out");
    }

    private TranscriptionResult downloadTranscriptionFiles(String filesUrl) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(filesUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Ocp-Apim-Subscription-Key", speechConfig.getKey());

            Map<String, Object> filesResponse = objectMapper.readValue(
                    connection.getInputStream(), Map.class);

            List<Map<String, Object>> values = (List<Map<String, Object>>) filesResponse.get("values");

            for (Map<String, Object> file : values) {
                String kind = (String) file.get("kind");
                if ("Transcription".equals(kind)) {
                    Map<String, Object> links = (Map<String, Object>) file.get("links");
                    String contentUrl = (String) links.get("contentUrl");
                    return downloadTranscriptionContent(contentUrl);
                }
            }

            throw new RuntimeException("No transcription file found");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private TranscriptionResult downloadTranscriptionContent(String contentUrl) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(contentUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            return objectMapper.readValue(connection.getInputStream(), TranscriptionResult.class);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<SpeakerSegment> parseTranscriptionResult(TranscriptionResult result) {
        List<SpeakerSegment> segments = new ArrayList<>();

        if (result.getRecognizedPhrases() != null) {
            for (TranscriptionResult.RecognizedPhrase phrase : result.getRecognizedPhrases()) {
                if (phrase.getNBest() != null && !phrase.getNBest().isEmpty()) {
                    TranscriptionResult.NBest best = phrase.getNBest().get(0);

                    String speakerId = "Speaker " + phrase.getSpeaker();
                    String text = best.getDisplay();
                    long offset = phrase.getOffsetInTicks() / 10000;
                    long duration = phrase.getDurationInTicks() / 10000;
                    double confidence = best.getConfidence();

                    segments.add(new SpeakerSegment(speakerId, text, offset, duration, confidence));
                }
            }
        }

        segments.sort(Comparator.comparingLong(SpeakerSegment::getOffset));
        return segments;
    }

    private String readErrorResponse(HttpURLConnection connection) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getErrorStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }

    public TranscriptionJob getJob(String jobId) {
        return jobStore.get(jobId);
    }

    public List<TranscriptionJob> getRecentJobs(int limit) {
        return jobStore.values().stream()
                .sorted((j1, j2) -> j2.getCreatedAt().compareTo(j1.getCreatedAt()))
                .limit(limit)
                .toList();
    }

    public List<BlobStorageService.ProcessedFileInfo> getProcessedFiles() {
        return blobStorageService.getProcessedFiles();
    }

    public static class SpeakerSegment {
        private String speakerId;
        private String text;
        private long offset;
        private long duration;
        private double confidence;

        public SpeakerSegment(String speakerId, String text, long offset, long duration, double confidence) {
            this.speakerId = speakerId;
            this.text = text;
            this.offset = offset;
            this.duration = duration;
            this.confidence = confidence;
        }

        // Getters
        public String getSpeakerId() { return speakerId; }
        public String getText() { return text; }
        public long getOffset() { return offset; }
        public long getDuration() { return duration; }
        public double getConfidence() { return confidence; }

        public String getFormattedTime() {
            long seconds = offset / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            long millis = offset % 1000;
            return String.format("%02d:%02d.%03d", minutes, seconds, millis);
        }
    }
}