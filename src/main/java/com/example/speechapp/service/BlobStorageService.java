package com.example.speechapp.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.example.speechapp.config.AzureBlobConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author  Binod Suman
 *
 * Purpose of BlobStorageService.java
 * BlobStorageService.java is a service class that handles all interactions with Azure Blob Storage.
 * It acts as an abstraction layer between the application and Azure's storage SDK, providing a clean
 * interface for file operations.
 *
 * Detailed Explanation
 * Primary Purpose
 * The service encapsulates all blob storage operations:
 *
 * Uploading audio files to Azure Blob Storage
 * Generating SAS (Shared Access Signature) URLs for secure access
 * Checking for existing files and transcripts
 * Saving and retrieving transcription results
 * Managing file cleanup
 * Listing processed files
 */
@Service
public class BlobStorageService {

    private static final Logger logger = LoggerFactory.getLogger(BlobStorageService.class);

    @Autowired
    private AzureBlobConfig blobConfig;

    private BlobContainerClient containerClient;

    private void initializeClient() {
        if (containerClient == null) {
            logger.info("Initializing Blob Storage client with connection string");
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(blobConfig.getConnectionString())
                    .buildClient();

            String containerName = blobConfig.getContainerName();
            containerClient = blobServiceClient.getBlobContainerClient(containerName);

            if (!containerClient.exists()) {
                logger.info("Creating container: {}", containerName);
                containerClient.create();
                logger.info("Container created successfully");
            } else {
                logger.info("Using existing container: {}", containerName);
            }
        }
    }

    /**
     * Check if a file already exists in blob storage
     */
    public boolean fileExists(String filename) {
        initializeClient();
        String safeFilename = sanitizeFilename(filename);
        BlobClient blobClient = containerClient.getBlobClient(safeFilename);
        return blobClient.exists();
    }

    /**
     * Check if transcript exists for a file
     */
    public boolean transcriptExists(String filename) {
        initializeClient();
        String transcriptName = getTranscriptFilename(filename);
        BlobClient blobClient = containerClient.getBlobClient(transcriptName);
        return blobClient.exists();
    }

    /**
     * Get transcript content for a file
     */
    public String getTranscriptContent(String filename) {
        initializeClient();
        String transcriptName = getTranscriptFilename(filename);
        BlobClient blobClient = containerClient.getBlobClient(transcriptName);

        if (!blobClient.exists()) {
            return null;
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            blobClient.downloadStream(outputStream);
            return outputStream.toString("UTF-8");
        } catch (Exception e) {
            logger.error("Error downloading transcript: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get list of all processed files (with transcripts)
     */
    public List<ProcessedFileInfo> getProcessedFiles() {
        initializeClient();
        List<ProcessedFileInfo> processedFiles = new ArrayList<>();

        for (BlobItem blobItem : containerClient.listBlobs()) {
            String blobName = blobItem.getName();
            if (blobName.endsWith("_transcript.json")) {
                String audioFile = blobName.replace("_transcript.json", ".wav");
                ProcessedFileInfo info = new ProcessedFileInfo();
                info.setAudioFile(audioFile);
                info.setTranscriptFile(blobName);
                info.setLastModified(blobItem.getProperties().getLastModified());
                info.setSize(blobItem.getProperties().getContentLength());
                processedFiles.add(info);
            }
        }

        return processedFiles;
    }

    /**
     * Upload file and generate SAS URL
     */
    public String uploadFileAndGenerateSas(MultipartFile file, String originalFilename) throws IOException {
        logger.info("Starting file upload process for: {}", originalFilename);
        initializeClient();

        String safeFilename = sanitizeFilename(originalFilename);
        String blobName = safeFilename;

        logger.info("Uploading file as blob: {}", blobName);

        BlobClient blobClient = containerClient.getBlobClient(blobName);
        if (blobClient.exists()) {
            logger.info("File already exists, overwriting: {}", blobName);
        }

        // Set content type for WAV file
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType("audio/wav");

        // Upload file using InputStream
        try (InputStream inputStream = file.getInputStream()) {
            blobClient.upload(inputStream, file.getSize(), true);

            // Set content type after upload
            blobClient.setHttpHeaders(headers);
        }

        logger.info("File uploaded successfully. Size: {} bytes", file.getSize());

        // Generate SAS token
        BlobSasPermission sasPermission = new BlobSasPermission()
                .setReadPermission(true)
                .setListPermission(true);

        OffsetDateTime expiryTime = OffsetDateTime.now().plus(Duration.ofHours(1));
        BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiryTime, sasPermission);

        String sasToken = blobClient.generateSas(sasValues);
        String fullUrl = blobClient.getBlobUrl() + "?" + sasToken;

        logger.info("SAS URL generated. Expires at: {}", expiryTime.format(DateTimeFormatter.ISO_DATE_TIME));

        return fullUrl;
    }

    /**
     * Save transcription result
     */
    public String saveTranscriptionResult(String originalFilename, String transcriptionJson) {
        logger.info("Saving transcription result for: {}", originalFilename);
        initializeClient();

        String transcriptName = getTranscriptFilename(originalFilename);
        logger.info("Saving transcript as: {}", transcriptName);

        BlobClient blobClient = containerClient.getBlobClient(transcriptName);

        // Set content type for JSON file
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType("application/json");

        // Upload JSON content
        byte[] jsonBytes = transcriptionJson.getBytes();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonBytes)) {
            blobClient.upload(inputStream, jsonBytes.length, true);
            blobClient.setHttpHeaders(headers);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        logger.info("Transcript saved successfully");
        return blobClient.getBlobUrl();
    }

    /**
     * Delete file from blob storage
     */
    public boolean deleteFile(String blobUrlWithSas) {
        logger.info("Attempting to delete blob: {}", blobUrlWithSas);
        try {
            initializeClient();
            String urlWithoutSas = blobUrlWithSas.contains("?")
                    ? blobUrlWithSas.substring(0, blobUrlWithSas.indexOf("?"))
                    : blobUrlWithSas;

            String blobName = urlWithoutSas.substring(urlWithoutSas.lastIndexOf("/") + 1);
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            boolean deleted = blobClient.deleteIfExists();
            if (deleted) {
                logger.info("Blob deleted successfully: {}", blobName);
            }
            return deleted;
        } catch (Exception e) {
            logger.error("Error deleting blob: {}", e.getMessage());
            return false;
        }
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    private String getTranscriptFilename(String originalFilename) {
        String baseName = originalFilename;
        if (baseName.toLowerCase().endsWith(".wav")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        return baseName + "_transcript.json";
    }

    /**
     * Inner class for processed file info
     */
    public static class ProcessedFileInfo {
        private String audioFile;
        private String transcriptFile;
        private OffsetDateTime lastModified;
        private long size;

        // Getters and setters
        public String getAudioFile() { return audioFile; }
        public void setAudioFile(String audioFile) { this.audioFile = audioFile; }

        public String getTranscriptFile() { return transcriptFile; }
        public void setTranscriptFile(String transcriptFile) { this.transcriptFile = transcriptFile; }

        public OffsetDateTime getLastModified() { return lastModified; }
        public void setLastModified(OffsetDateTime lastModified) { this.lastModified = lastModified; }

        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }

        public String getFormattedSize() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }

        public String getFormattedDate() {
            return lastModified.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }
}