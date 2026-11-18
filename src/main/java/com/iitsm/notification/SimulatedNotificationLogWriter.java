package com.iitsm.notification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Package helper writing simulated Email and SMS dispatches cleanly to disk. */
final class SimulatedNotificationLogWriter {

    private SimulatedNotificationLogWriter() {
    }

    /** Appends a single timestamped line to the target log file, creating parent directories if needed. */
    static void appendLine(String filePath, String channel, String target, String subject, String message)
            throws IOException {
        Path path = Path.of(filePath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        String line = String.format("%s | %s | to=%s | subject=%s | %s%n",
                Instant.now(), channel, target, subject, message.replace('\n', ' '));
        Files.writeString(path, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
