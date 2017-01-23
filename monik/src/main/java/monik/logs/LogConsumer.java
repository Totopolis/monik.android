package monik.logs;

public interface LogConsumer {
    void consume(LogEntry logEntry);
    void close();
}
