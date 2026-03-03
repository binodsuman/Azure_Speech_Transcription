package com.example.speechapp.controller;

import com.example.speechapp.model.TranscriptionJob;
import com.example.speechapp.service.SpeechService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Binod Suman
 *
 * Purpose of SpeechController.java
 * SpeechController.java is the web controller/REST controller that handles all HTTP requests, manages user
 * interactions, and orchestrates the flow between the UI and backend services. It serves as the entry point
 * for all client communications.
 *
 * Detailed Explanation
 * Primary Purpose
 * The controller acts as the presentation layer that:
 *
 * Serves web pages (UI endpoints)
 * Handles file uploads
 * Provides REST APIs for asynchronous status checking
 * Manages request validation and error handling
 * Routes requests to appropriate services
 *
 * Prepares data for view templates
 */

@Controller
public class SpeechController {

    @Autowired
    private SpeechService speechService;

    private final Map<String, TranscriptionJob> jobStore = new ConcurrentHashMap<>();

    @GetMapping("/")
    public String index() {
        return "upload";
    }

    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "email", required = false) String email,
                                   RedirectAttributes redirectAttributes) {
        try {
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
                return "redirect:/";
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.equals("audio/wav")) {
                redirectAttributes.addFlashAttribute("error", "Please upload a WAV file");
                return "redirect:/";
            }

            if (file.getSize() > 200 * 1024 * 1024) {
                redirectAttributes.addFlashAttribute("error", "File size exceeds 200MB limit");
                return "redirect:/";
            }

            TranscriptionJob job = speechService.startTranscriptionJob(file, email);
            jobStore.put(job.getJobId(), job);

            return "redirect:/status/" + job.getJobId();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/";
        }
    }

    @GetMapping("/status/{jobId}")
    public String getJobStatus(@PathVariable String jobId, Model model) {
        TranscriptionJob job = jobStore.get(jobId);

        if (job == null) {
            model.addAttribute("error", "Job not found");
            return "upload";
        }

        model.addAttribute("job", job);

        if (job.getStatus() == TranscriptionJob.JobStatus.COMPLETED) {
            model.addAttribute("segments", job.getSegments());
        }

        return "status";
    }

    @GetMapping("/api/status/{jobId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getJobStatusApi(@PathVariable String jobId) {
        TranscriptionJob job = jobStore.get(jobId);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", job.getJobId());
        response.put("status", job.getStatus().toString());
        response.put("message", job.getMessage());
        response.put("progress", job.getProgress());
        response.put("fileName", job.getFileName());
        response.put("createdAt", job.getCreatedAt());

        if (job.getStatus() == TranscriptionJob.JobStatus.COMPLETED) {
            response.put("segments", job.getSegments());
            response.put("completedAt", job.getCompletedAt());
            response.put("totalSpeakers", job.getTotalSpeakers());
            response.put("totalDuration", job.getTotalDuration());
        } else if (job.getStatus() == TranscriptionJob.JobStatus.FAILED) {
            response.put("error", job.getErrorMessage());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/jobs")
    public String listJobs(Model model) {
        List<TranscriptionJob> recentJobs = jobStore.values().stream()
                .sorted((j1, j2) -> j2.getCreatedAt().compareTo(j1.getCreatedAt()))
                .limit(10)
                .toList();

        model.addAttribute("jobs", recentJobs);
        return "jobs";
    }
}