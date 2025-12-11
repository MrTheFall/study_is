package com.krusty.crab.exception;

public class MenuException extends RuntimeException {
    public MenuException(String message) {
        super(message);
    }
    
    public MenuException(String message, Throwable cause) {
        super(message, cause);
    }
}

