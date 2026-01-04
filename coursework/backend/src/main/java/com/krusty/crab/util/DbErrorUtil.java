package com.krusty.crab.util;

public final class DbErrorUtil {

    private DbErrorUtil() {}

    public static String extractMeaningfulMessage(Throwable ex) {
        if (ex == null) {
            return null;
        }

        String message = ex.getMessage();
        if (message != null) {
            int errorIndex = message.indexOf("ERROR:");
            if (errorIndex >= 0) {
                return message.substring(errorIndex + "ERROR:".length()).trim();
            }
        }

        Throwable cause = ex.getCause();
        if (cause != null && cause != ex) {
            String causeMessage = extractMeaningfulMessage(cause);
            if (causeMessage != null && !causeMessage.isBlank()) {
                return causeMessage;
            }
        }

        return message;
    }
}
