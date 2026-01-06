package com.example.emrtdreader.sdk.error;

public class PassportReadException extends Exception {
    public PassportReadException(String message) { super(message); }
    public PassportReadException(String message, Throwable cause) { super(message, cause); }

    public static class TagNotIsoDep extends PassportReadException {
        public TagNotIsoDep() { super("Tag is not IsoDep"); }
    }

    public static class ReadFailed extends PassportReadException {
        public ReadFailed(Throwable cause) { super("Read failed", cause); }
        public ReadFailed(String message, Throwable cause) { super(message, cause); }
    }

    public static class AccessFailed extends PassportReadException {
        public AccessFailed(String message, Throwable cause) { super(message, cause); }
    }
}
