package com.example.speechapp.exception;

public class SpeechDiarizationException extends RuntimeException {

    public SpeechDiarizationException(String message) {
        super(message);
    }

    public SpeechDiarizationException(String message, Throwable cause) {
        super(message, cause);
    }
}