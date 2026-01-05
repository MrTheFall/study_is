package com.krusty.crab.security;

public enum UserType {
    CLIENT("CLIENT"),
    EMPLOYEE("EMPLOYEE");

    private final String value;

    UserType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static UserType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("UserType is required");
        }
        for (UserType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown UserType: " + value);
    }
}
