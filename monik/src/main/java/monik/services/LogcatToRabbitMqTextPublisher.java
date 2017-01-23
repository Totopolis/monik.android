package monik.services;

import monik.logs.LogEntry;
import monik.logs.LogUtils;

public class LogcatToRabbitMqTextPublisher extends LogcatToRabbitMqPublisher {

    @Override
    protected byte[] logEntryToBytes(LogEntry logEntry) {
        final String text = logEntryToText(logEntry);
        return text != null ? text.getBytes() : null;
    }

    public String logEntryToText(LogEntry logEntry) {
        return defaultLogEntryToText(logEntry);
    }

    public static String defaultLogEntryToText(LogEntry logEntry) {
        return LogUtils.toText(logEntry);
    }
}
