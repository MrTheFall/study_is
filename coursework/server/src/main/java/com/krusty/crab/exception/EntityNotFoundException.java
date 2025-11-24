package com.krusty.crab.exception;

public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String message) {
        super(message);
    }
    
    public EntityNotFoundException(String entity, Integer id) {
        super(String.format("%s with id %d not found", entity, id));
    }
}

