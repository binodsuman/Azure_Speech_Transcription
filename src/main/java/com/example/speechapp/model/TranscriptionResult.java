package com.example.speechapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * @author Binod Suman
 * Parses JSON response from Azure
 * Azure API response mapping with nested classes
 *
 * Purpose of TranscriptionResult.java
 * TranscriptionResult.java is a mapping class that deserializes the JSON response from Azure Speech Service's
 * batch transcription API into Java objects. It serves as a Data Transfer Object (DTO) that captures the
 * complete transcription output including speaker diarization results.
 *
 * Detailed Explanation
 * Primary Purpose
 * The class maps the complex nested JSON structure returned by Azure Speech Service when a batch transcription
 * job completes. This allows the application to easily access:
 *
 * Who spoke when (speaker identification)
 * What they said (transcribed text)
 * When they said it (timestamps)
 * How confident the model is (confidence scores)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TranscriptionResult {

    @JsonProperty("source")
    private String source;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("durationInTicks")
    private long durationInTicks;

    @JsonProperty("duration")
    private String duration;

    @JsonProperty("combinedRecognizedPhrases")
    private List<CombinedPhrase> combinedRecognizedPhrases;

    @JsonProperty("recognizedPhrases")
    private List<RecognizedPhrase> recognizedPhrases;

    // Getters and Setters
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public long getDurationInTicks() { return durationInTicks; }
    public void setDurationInTicks(long durationInTicks) { this.durationInTicks = durationInTicks; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public List<CombinedPhrase> getCombinedRecognizedPhrases() { return combinedRecognizedPhrases; }
    public void setCombinedRecognizedPhrases(List<CombinedPhrase> combinedRecognizedPhrases) {
        this.combinedRecognizedPhrases = combinedRecognizedPhrases;
    }

    public List<RecognizedPhrase> getRecognizedPhrases() { return recognizedPhrases; }
    public void setRecognizedPhrases(List<RecognizedPhrase> recognizedPhrases) {
        this.recognizedPhrases = recognizedPhrases;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CombinedPhrase {
        @JsonProperty("channel")
        private int channel;

        @JsonProperty("lexical")
        private String lexical;

        @JsonProperty("itn")
        private String itn;

        @JsonProperty("maskedITN")
        private String maskedITN;

        @JsonProperty("display")
        private String display;

        public int getChannel() { return channel; }
        public void setChannel(int channel) { this.channel = channel; }

        public String getLexical() { return lexical; }
        public void setLexical(String lexical) { this.lexical = lexical; }

        public String getItn() { return itn; }
        public void setItn(String itn) { this.itn = itn; }

        public String getMaskedITN() { return maskedITN; }
        public void setMaskedITN(String maskedITN) { this.maskedITN = maskedITN; }

        public String getDisplay() { return display; }
        public void setDisplay(String display) { this.display = display; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecognizedPhrase {
        @JsonProperty("recognitionStatus")
        private String recognitionStatus;

        @JsonProperty("speaker")
        private int speaker;

        @JsonProperty("channel")
        private int channel;

        @JsonProperty("offset")
        private String offset;

        @JsonProperty("duration")
        private String duration;

        @JsonProperty("offsetInTicks")
        private long offsetInTicks;

        @JsonProperty("durationInTicks")
        private long durationInTicks;

        @JsonProperty("nBest")
        private List<NBest> nBest;

        public String getRecognitionStatus() { return recognitionStatus; }
        public void setRecognitionStatus(String recognitionStatus) { this.recognitionStatus = recognitionStatus; }

        public int getSpeaker() { return speaker; }
        public void setSpeaker(int speaker) { this.speaker = speaker; }

        public int getChannel() { return channel; }
        public void setChannel(int channel) { this.channel = channel; }

        public String getOffset() { return offset; }
        public void setOffset(String offset) { this.offset = offset; }

        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }

        public long getOffsetInTicks() { return offsetInTicks; }
        public void setOffsetInTicks(long offsetInTicks) { this.offsetInTicks = offsetInTicks; }

        public long getDurationInTicks() { return durationInTicks; }
        public void setDurationInTicks(long durationInTicks) { this.durationInTicks = durationInTicks; }

        public List<NBest> getNBest() { return nBest; }
        public void setNBest(List<NBest> nBest) { this.nBest = nBest; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NBest {
        @JsonProperty("confidence")
        private double confidence;

        @JsonProperty("lexical")
        private String lexical;

        @JsonProperty("itn")
        private String itn;

        @JsonProperty("maskedITN")
        private String maskedITN;

        @JsonProperty("display")
        private String display;

        @JsonProperty("displayWords")
        private List<DisplayWord> displayWords;

        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }

        public String getLexical() { return lexical; }
        public void setLexical(String lexical) { this.lexical = lexical; }

        public String getItn() { return itn; }
        public void setItn(String itn) { this.itn = itn; }

        public String getMaskedITN() { return maskedITN; }
        public void setMaskedITN(String maskedITN) { this.maskedITN = maskedITN; }

        public String getDisplay() { return display; }
        public void setDisplay(String display) { this.display = display; }

        public List<DisplayWord> getDisplayWords() { return displayWords; }
        public void setDisplayWords(List<DisplayWord> displayWords) { this.displayWords = displayWords; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DisplayWord {
        @JsonProperty("displayText")
        private String displayText;

        @JsonProperty("offset")
        private String offset;

        @JsonProperty("duration")
        private String duration;

        @JsonProperty("offsetInTicks")
        private long offsetInTicks;

        @JsonProperty("durationInTicks")
        private long durationInTicks;

        public String getDisplayText() { return displayText; }
        public void setDisplayText(String displayText) { this.displayText = displayText; }

        public String getOffset() { return offset; }
        public void setOffset(String offset) { this.offset = offset; }

        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }

        public long getOffsetInTicks() { return offsetInTicks; }
        public void setOffsetInTicks(long offsetInTicks) { this.offsetInTicks = offsetInTicks; }

        public long getDurationInTicks() { return durationInTicks; }
        public void setDurationInTicks(long durationInTicks) { this.durationInTicks = durationInTicks; }
    }
}