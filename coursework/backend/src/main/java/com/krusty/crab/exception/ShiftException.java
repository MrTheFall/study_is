package com.krusty.crab.exception;

public class ShiftException extends RuntimeException {
    public ShiftException(String message) {
        super(message);
    }
    
    public ShiftException(String message, Throwable cause) {
        super(message, cause);
    }
}

