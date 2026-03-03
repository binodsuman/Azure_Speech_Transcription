# 🎤 Speech Diarization with Azure Cognitive Services

<img width="1376" height="768" alt="Customer_Support_Transcription" src="https://github.com/user-attachments/assets/10460935-106d-4ded-bc6d-c1d34b8aeb25" />


A Spring Boot application that performs speaker diarization on WAV files using Azure Speech Services. The application identifies different speakers in an audio file and provides timestamped transcripts with speaker labels.

## 📋 Table of Contents
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
  - [Azure Configuration Properties](#azure-configuration-properties)
  - [Application Properties](#application-properties)
- [Java Classes Overview](#java-classes-overview)
- [Detailed Class Descriptions](#detailed-class-descriptions)
- [API Endpoints](#api-endpoints)
- [REST API](#rest-api)
- [Usage Guide](#usage-guide)
- [Troubleshooting](#troubleshooting)
- [Azure Setup Guide](#azure-setup-guide)
- [File Naming Convention](#file-naming-convention)
- [Security Features](#security-features)
- [Performance Considerations](#performance-considerations)
- [Monitoring](#monitoring)

## ✨ Features

- **Speaker Diarization**: Identifies different speakers in audio files
- **File Persistence**: Stores original files and transcripts in Azure Blob Storage with original filenames
- **Duplicate Detection**: Checks if a file was already processed and returns existing transcript
- **Asynchronous Processing**: Handles long-running transcription jobs in the background
- **Real-time Status**: Live progress tracking with auto-refreshing UI
- **Email Notifications**: Optional email alerts when transcription completes
- **Detailed Logging**: Comprehensive logs for debugging and monitoring
- **Job History**: View status of all transcription jobs
- **REST API**: Full API support for integration with other applications

## 📦 Prerequisites

- Java 17 or higher
- Maven 3.6+
- Azure Subscription
- Azure Speech Services resource
- Azure Storage Account

## 🚀 Quick Start

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd speech-diarization-app
   ```

2. **Configure Azure credentials**

   Edit `src/main/resources/application.properties`:
   ```properties
   azure.speech.key=YOUR_SPEECH_KEY
   azure.speech.region=eastus
   azure.storage.connection-string=YOUR_STORAGE_CONNECTION_STRING
   azure.storage.container-name=speech-files
   ```

3. **Build the application**
   ```bash
   mvn clean install
   ```

4. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

5. **Access the application**

   Open browser: `http://localhost:8080`

## ⚙️ Configuration

### Azure Configuration Properties

| Property | Description | Where to Find in Azure Portal |
|----------|-------------|-------------------------------|
| `azure.speech.key` | Azure Speech API key | Speech Service → Keys and Endpoint → KEY 1 |
| `azure.speech.region` | Azure region (e.g., eastus) | Speech Service → Overview → Location |
| `azure.speech.language` | Language code for transcription | Default: en-US (change as needed) |
| `azure.storage.connection-string` | Blob storage connection string | Storage Account → Access keys → Connection string |
| `azure.storage.container-name` | Container name for storing files | Storage Account → Containers → Your container name |

### Application Properties

| Property | Description | Default Value | Required |
|----------|-------------|---------------|----------|
| `server.port` | Server port | 8080 | No |
| `app.base-url` | Base URL for email links | http://localhost:8080 | No |
| `spring.servlet.multipart.max-file-size` | Maximum file upload size | 200MB | No |
| `spring.servlet.multipart.max-request-size` | Maximum request size | 200MB | No |
| `spring.thymeleaf.cache` | Thymeleaf template caching | false | No |
| `job.polling.max-attempts` | Max polling attempts for Azure job | 120 | No |
| `job.status.update-interval` | Polling interval in seconds | 5 | No |
| `spring.mail.enabled` | Enable email notifications | false | No |
| `spring.mail.host` | SMTP host | smtp.gmail.com | If email enabled |
| `spring.mail.port` | SMTP port | 587 | If email enabled |
| `spring.mail.username` | Email username | - | If email enabled |
| `spring.mail.password` | Email password | - | If email enabled |
| `logging.level.com.example.speechapp` | Application log level | DEBUG | No |
| `logging.file.name` | Log file location | logs/speech-diarization.log | No |

## 📚 Java Classes Overview

| Class | Package | Purpose | Key Methods |
|-------|---------|---------|-------------|
| **SpeechDiarizationApplication** | `com.example.speechapp` | Main Spring Boot application class with async support | `main()` |
| **AzureSpeechConfig** | `config` | Speech service configuration holder | Getters/setters for key, region, language |
| **AzureBlobConfig** | `config` | Blob storage configuration holder | Getters/setters for connection string, container |
| **SpeechController** | `controller` | REST endpoints and UI routing | `upload()`, `getJobStatus()`, `listJobs()`, `checkFile()` |
| **SpeechService** | `service` | Core transcription logic with async processing | `startTranscriptionJob()`, `processJobAsync()`, `pollForTranscriptionResult()` |
| **BlobStorageService** | `service` | Azure Blob operations with file checking | `uploadFileAndGenerateSas()`, `fileExists()`, `transcriptExists()`, `saveTranscriptionResult()` |
| **EmailService** | `service` | Email notification service | `sendCompletionNotification()`, `sendFailureNotification()` |
| **TranscriptionJob** | `model` | Job state management with enum states | Status tracking, progress updates, formatted file size |
| **TranscriptionResult** | `model` | Azure API response mapping with nested classes | Parses JSON response from Azure |

## 📖 Detailed Class Descriptions

### 📁 config Package

**AzureSpeechConfig.java**
- **Purpose**: Configuration properties holder for Azure Speech Service
- **Properties**:
    - `key`: Azure Speech API key
    - `region`: Azure region (eastus, westus2, etc.)
    - `language`: Language code for transcription (default: en-US)
- **Usage**: Auto-wired into SpeechService for making API calls to Azure Speech

**AzureBlobConfig.java**
- **Purpose**: Configuration properties holder for Azure Blob Storage
- **Properties**:
    - `connectionString`: Full Azure Storage connection string
    - `containerName`: Blob container name for storing files
- **Usage**: Auto-wired into BlobStorageService for all storage operations
- **Note**: Container is automatically created if it doesn't exist

### 📁 controller Package

**SpeechController.java**
- **Purpose**: Handles all HTTP requests and routes them to appropriate services
- **Key Methods**:
    - `index()`: Serves the upload page
    - `handleFileUpload()`: Processes file upload, checks for existing transcripts, starts jobs
    - `getJobStatus()`: Displays job status page with auto-refresh
    - `getJobStatusApi()`: REST endpoint for job status JSON
    - `listJobs()`: Shows recent transcription jobs
    - `checkFileExistence()`: API endpoint to check if file already processed
- **Features**: File validation, error handling, redirect logic

### 📁 service Package

**SpeechService.java**
- **Purpose**: Core business logic for speech transcription
- **Key Methods**:
    - `startTranscriptionJob()`: Creates new job and returns immediately
    - `processJobAsync()`: Async method that handles the entire transcription pipeline
    - `createTranscription()`: Calls Azure API to create transcription job
    - `pollForTranscriptionResult()`: Polls Azure for job completion
    - `parseTranscriptionResult()`: Converts Azure JSON to speaker segments
- **Features**: Async processing, progress tracking, detailed logging, error handling

**BlobStorageService.java**
- **Purpose**: All Azure Blob Storage operations
- **Key Methods**:
    - `fileExists()`: Checks if audio file exists in blob storage
    - `transcriptExists()`: Checks if transcript exists for a file
    - `getTranscriptContent()`: Retrieves existing transcript JSON
    - `uploadFileAndGenerateSas()`: Uploads file and returns SAS URL
    - `saveTranscriptionResult()`: Saves transcript with _transcript.json suffix
    - `getProcessedFiles()`: Lists all processed files with metadata
- **Features**: SAS URL generation, automatic container creation, filename sanitization

**EmailService.java**
- **Purpose**: Handles email notifications
- **Key Methods**:
    - `sendCompletionNotification()`: Sends success email with job details
    - `sendFailureNotification()`: Sends failure email with error message
- **Features**: Conditional sending, formatted email bodies, error handling

### 📁 model Package

**TranscriptionJob.java**
- **Purpose**: Tracks state of each transcription job
- **States**:
    - `UPLOADING`: File being uploaded to blob storage
    - `PROCESSING`: Azure processing the audio
    - `COMPLETED`: Transcription complete with results
    - `FAILED`: Job failed with error message
- **Properties**: jobId, fileName, fileSize, status, progress, timestamps, segments
- **Methods**: `getFormattedFileSize()`, status management, progress tracking

**TranscriptionResult.java**
- **Purpose**: Maps Azure Speech API JSON response to Java objects
- **Nested Classes**:
    - `CombinedPhrase`: Combined transcription for all speakers
    - `RecognizedPhrase`: Individual phrase with speaker ID
    - `NBest`: Best recognition result with confidence
    - `DisplayWord`: Word-level timestamps
- **Features**: Comprehensive JSON mapping with @JsonIgnoreProperties

## 🌐 API Endpoints

### Web UI Endpoints

| Method | URL | Description | Parameters |
|--------|-----|-------------|------------|
| GET | `/` | Upload page with file dropzone | None |
| GET | `/status/{jobId}` | Real-time job status page | `jobId`: Job identifier |
| GET | `/jobs` | List recent transcription jobs | None |

### REST API Endpoints

| Method | URL | Description | Request Body | Response |
|--------|-----|-------------|--------------|----------|
| POST | `/upload` | Upload file and start job | `file`: WAV file<br>`email`: (optional) | Redirect to `/status/{jobId}` |
| GET | `/api/status/{jobId}` | Get job status as JSON | None | Job details with segments |
| POST | `/api/check-file` | Check if file already processed | `file`: WAV file | `{"exists": true/false}` |
| GET | `/api/transcript/{filename}` | Get transcript JSON | None | Full transcript JSON |
| GET | `/api/jobs` | List all jobs | None | Array of job objects |

## 📡 REST API

### Check File Existence
```bash
curl -X POST -F "file=@audio.wav" http://localhost:8080/api/check-file
```
Response:
```json
{
  "exists": true,
  "filename": "audio.wav"
}
```

### Get Job Status
```bash
curl http://localhost:8080/api/status/job-123
```
Response:
```json
{
  "jobId": "job-123",
  "status": "COMPLETED",
  "progress": 100,
  "message": "Transcription completed",
  "fileName": "meeting.wav",
  "totalSpeakers": 3,
  "totalDuration": 125000,
  "segments": [
    {
      "speakerId": "Speaker 1",
      "text": "Hello, welcome to the meeting",
      "offset": 0,
      "duration": 2500,
      "confidence": 0.95,
      "formattedTime": "00:00.000"
    }
  ]
}
```

### Get Transcript JSON
```bash
curl http://localhost:8080/api/transcript/meeting.wav
```
Returns the full Azure Speech JSON response.

### List All Jobs
```bash
curl http://localhost:8080/api/jobs
```
Returns array of recent jobs with status.

## 📖 Usage Guide

### 1. Uploading a File

1. Navigate to `http://localhost:8080`
2. Drag & drop a WAV file onto the dropzone or click to select
3. (Optional) Enter email address for notification when complete
4. Click "Start Transcription" button
5. You'll be redirected to the status page

### 2. File Existence Check

The application automatically checks if a file was previously processed:

- **If file is new**: Normal processing begins
- **If file exists with transcript**:
    - Warning message appears
    - "View Existing Transcript" button shown
    - Option to upload anyway (will overwrite)
    - Submit button is disabled until you choose

### 3. Monitoring Progress

The status page provides real-time updates:

- **Progress Bar**: Visual indication of completion (0-100%)
- **Status Message**: Current step (Uploading, Processing, Parsing)
- **Job Details**: File name, size, creation time
- **Auto-refresh**: Page refreshes every 5 seconds during processing
- **Logs**: Detailed progress in server console

### 4. Viewing Results

When transcription completes, you'll see:

- **Summary Statistics**:
    - Total number of segments
    - Number of unique speakers detected
    - Total duration of speech
- **Speaker Segments**:
    - Color-coded by speaker (Speaker 1-5)
    - Transcribed text with confidence percentage
    - Start timestamp and duration
    - Sorted chronologically

### 5. Working with Existing Transcripts

When uploading a previously processed file:

1. Warning banner appears at top
2. Click "View Existing Transcript" to see results immediately
3. Click "Upload Different File" to select another file
4. To reprocess, select the file again and confirm overwrite

### 6. Email Notifications

If email is configured and provided:

- **On Success**: Email with job details and link to results
- **On Failure**: Email with error message
- Links point to the status page for full results

### 7. Viewing Job History

Navigate to `/jobs` to see:
- List of all recent jobs
- Current status of each job
- Progress percentage
- Quick links to job details
- Color-coded status badges

### 8. Accessing Raw JSON

For integration or debugging:
- Click "View Transcript" on job page
- Direct API access via `/api/transcript/{filename}`
- Raw Azure Speech JSON format preserved

## 🔍 Troubleshooting

### Common Issues and Solutions

| Issue | Symptom | Solution |
|-------|---------|----------|
| **Connection refused** | "Failed to create transcription" | Check Azure credentials in properties |
| **File too large** | Upload fails | Max file size is 200MB (configurable) |
| **Wrong file type** | "Please upload a WAV file" | Only WAV format supported |
| **Transcription timeout** | Job stuck in PROCESSING | Increase `job.polling.max-attempts` |
| **Blob storage errors** | "Error uploading to blob" | Verify connection string and container permissions |
| **No speech detected** | Empty results | Check audio quality and content |
| **Email not sending** | No notifications | Verify SMTP settings and enable spring.mail.enabled |

### Logs

Logs are written to two locations:

**Console Output** (real-time):
```
2024-01-01 12:00:01 - ================================================================================
2024-01-01 12:00:01 - PROCESSING FILE: meeting.wav
2024-01-01 12:00:01 - ================================================================================
2024-01-01 12:00:02 - [10%] Uploading to Azure Blob Storage...
2024-01-01 12:00:03 - ✓ File uploaded successfully
2024-01-01 12:00:03 - [25%] Creating Azure Speech transcription job...
2024-01-01 12:00:04 - ✓ Transcription job created
```

**Log File** (`logs/speech-diarization.log`):
```
2024-01-01 12:00:01 [main] INFO  com.example.speechapp.service.SpeechService - ================================================================================
2024-01-01 12:00:01 [main] INFO  com.example.speechapp.service.SpeechService - PROCESSING FILE: meeting.wav
2024-01-01 12:00:01 [main] INFO  com.example.speechapp.service.SpeechService - ================================================================================
```

### Debug Mode

Enable debug logging for more details:
```properties
logging.level.com.example.speechapp=DEBUG
```

View logs in real-time:
```bash
tail -f logs/speech-diarization.log
```

## ☁️ Azure Setup Guide

### Step 1: Create Speech Service

1. Go to [Azure Portal](https://portal.azure.com)
2. In top search bar, type "Speech Services"
3. Click "Create" under Speech Services
4. Fill in the form:
    - **Subscription**: Select your Azure subscription
    - **Resource group**: Create new or select existing
    - **Region**: Choose nearest (e.g., East US)
    - **Name**: Your service name (e.g., "my-speech-service")
    - **Pricing tier**: Free F0 (for testing) or Standard S0
5. Click "Review + create"
6. Click "Create"
7. Wait for deployment (1-2 minutes)
8. Click "Go to resource"

### Step 2: Get Speech Credentials

1. In your Speech Service resource, click "Keys and Endpoint" in left menu
2. Copy either "KEY 1" or "KEY 2" - this is your `azure.speech.key`
3. Note the "Location/Region" (e.g., "eastus") - this is your `azure.speech.region`

### Step 3: Create Storage Account

1. In Azure Portal, search "Storage accounts"
2. Click "Create"
3. Fill in basics:
    - **Subscription**: Your subscription
    - **Resource group**: Use same as Speech Service
    - **Storage account name**: Globally unique name (e.g., "mydiarizationstorage")
    - **Region**: Same as Speech Service
    - **Performance**: Standard
    - **Redundancy**: Locally-redundant storage (LRS)
4. Click "Review + create"
5. Click "Create"
6. Wait for deployment
7. Click "Go to resource"

### Step 4: Get Storage Credentials

1. In your Storage Account, click "Access keys" in left menu under "Security + networking"
2. Click "Show" next to any key
3. Copy the "Connection string" - this is your `azure.storage.connection-string`
    - Format: `DefaultEndpointsProtocol=https;AccountName=...;AccountKey=...;EndpointSuffix=core.windows.net`

### Step 5: Create Container

1. In your Storage Account, click "Containers" in left menu under "Data storage"
2. Click "+ Container"
3. Name: `speech-files` (or your preferred name)
4. Public access level: "Private (no anonymous access)"
5. Click "Create"

### Step 6: Configure Application

Update `application.properties` with your values:
```properties
azure.speech.key=your-copied-key-here
azure.speech.region=eastus
azure.storage.connection-string=your-copied-connection-string-here
azure.storage.container-name=speech-files
```

### Azure Costs Estimation

| Service | Free Tier | Standard Tier |
|---------|-----------|---------------|
| Speech Service | 5 audio hours/month | $1.00 per audio hour |
| Storage Account | 5 GB free | $0.02 per GB/month |
| Bandwidth | 5 GB outbound free | $0.087 per GB |

## 📝 File Naming Convention

### Audio Files
- Original filename is preserved exactly as uploaded
- Example: `team-meeting-2024-01-15.wav`
- Special characters replaced with underscores for safety

### Transcript Files
- Format: `[original-name]_transcript.json`
- Example: `team-meeting-2024-01-15_transcript.json`
- Always saved in same container as audio file

### Example File Structure in Blob
```
speech-files/
├── meeting.wav
├── meeting_transcript.json
├── interview.wav
├── interview_transcript.json
└── conference_call.wav
└── conference_call_transcript.json
```

## 🔒 Security Features

### Authentication
- All Azure API calls use key-based authentication
- Keys stored in properties file (not in code)
- Support for Azure Key Vault (optional)

### File Access
- **SAS URLs**: Temporary access URLs with 1-hour expiry
- **Read-only**: SAS tokens only allow read operations
- **No public access**: Blob container is private
- **Auto-cleanup**: Temporary files deleted after processing

### Data Protection
- **In transit**: All HTTPS connections
- **At rest**: Azure Storage encryption
- **No local storage**: Files processed in memory, streamed to Azure

### Best Practices
1. Rotate API keys regularly
2. Use different keys for development/production
3. Enable Azure Defender for additional security
4. Monitor access logs in Azure Portal

## 🚦 Performance Considerations

### File Size Limits
- **Maximum**: 200MB (configurable)
- **Recommended**: < 100MB for optimal performance
- **Duration**: Up to 2 hours of audio

### Processing Times
| Audio Duration | Typical Processing Time |
|----------------|------------------------|
| 5 minutes | 30-60 seconds |
| 30 minutes | 2-3 minutes |
| 1 hour | 4-5 minutes |
| 2 hours | 8-10 minutes |

### Concurrent Jobs
| Azure Tier | Concurrent Transcriptions |
|------------|--------------------------|
| Free (F0) | 1 job at a time |
| Standard (S0) | 20 jobs at a time |

### Memory Usage
- Heap: ~256MB for application
- Per job: Additional ~50MB during processing
- Recommended: 1GB heap for production

### Scaling Recommendations
1. **Horizontal**: Run multiple instances behind load balancer
2. **Vertical**: Increase Azure Speech tier for more concurrency
3. **Database**: Replace in-memory job store with Redis/PostgreSQL
4. **Queue**: Add message queue for job distribution

## 📊 Monitoring

### Application Health Checks
- Endpoint: `/actuator/health` (if Spring Actuator enabled)
- Returns: UP/DOWN status with component details

### Key Metrics to Monitor
1. **Job Success Rate**: Percentage of completed vs failed jobs
2. **Average Processing Time**: Per audio minute
3. **Queue Length**: Number of pending jobs
4. **Error Rate**: Frequency of failures
5. **Azure Costs**: Speech and storage usage

### Azure Portal Monitoring

**Speech Service Metrics**:
- Successful requests
- Failed requests
- Audio hours processed
- Latency

**Storage Metrics**:
- Used capacity
- Number of blobs
- Transaction count
- Egress data

### Logging Levels
```properties
# Production
logging.level.com.example.speechapp=INFO

# Development/Debug
logging.level.com.example.speechapp=DEBUG
logging.level.com.azure=INFO
```

### Alerts to Configure
1. **High failure rate** (>10% over 1 hour)
2. **Long processing time** (>15 minutes for small files)
3. **Storage near capacity** (>80% used)
4. **Azure credits running low**

### Dashboard Example
```
SPEECH DIARIZATION DASHBOARD
============================
Active Jobs: 3
Completed Today: 47
Failed Today: 2
Success Rate: 95.7%

Top Speakers Detected:
- Speaker 1: 1,245 segments
- Speaker 2: 892 segments
- Speaker 3: 456 segments

Storage Used: 2.3 GB / 5 GB
Azure Costs (MTD): $45.67
```

---

**Built with** 💙 using Spring Boot and Azure Cognitive Services

**Version**: 1.0.0  
**Last Updated**: March 2024  
**Java Version**: 17  
**Spring Boot**: 3.1.5
```
