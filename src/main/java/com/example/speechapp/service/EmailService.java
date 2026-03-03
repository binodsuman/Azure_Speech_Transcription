package com.example.speechapp.service;

import com.example.speechapp.model.TranscriptionJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public void sendCompletionNotification(TranscriptionJob job) {
        if (!emailEnabled || mailSender == null || job.getEmail() == null) {
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(job.getEmail());
            message.setSubject("Speech Transcription Completed - " + job.getFileName());
            message.setText(buildCompletionEmailBody(job));

            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }

    public void sendFailureNotification(TranscriptionJob job, String errorMessage) {
        if (!emailEnabled || mailSender == null || job.getEmail() == null) {
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(job.getEmail());
            message.setSubject("Speech Transcription Failed - " + job.getFileName());
            message.setText(buildFailureEmailBody(job, errorMessage));

            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }

    private String buildCompletionEmailBody(TranscriptionJob job) {
        StringBuilder body = new StringBuilder();
        body.append("Your speech transcription job has completed successfully!\n\n");
        body.append("Job Details:\n");
        body.append("- Job ID: ").append(job.getJobId()).append("\n");
        body.append("- File: ").append(job.getFileName()).append("\n");
        body.append("- Size: ").append(job.getFormattedFileSize()).append("\n");
        body.append("- Speakers Detected: ").append(job.getTotalSpeakers()).append("\n");
        body.append("- Total Duration: ").append(job.getTotalDuration() / 1000).append(" seconds\n\n");
        body.append("View Results: ").append(baseUrl).append("/status/").append(job.getJobId()).append("\n");
        return body.toString();
    }

    private String buildFailureEmailBody(TranscriptionJob job, String errorMessage) {
        StringBuilder body = new StringBuilder();
        body.append("Your speech transcription job has failed.\n\n");
        body.append("Job Details:\n");
        body.append("- Job ID: ").append(job.getJobId()).append("\n");
        body.append("- File: ").append(job.getFileName()).append("\n");
        body.append("- Error: ").append(errorMessage).append("\n\n");
        body.append("Please try again or contact support if the issue persists.\n");
        return body.toString();
    }
}