package monik.logs;

public interface LogFilter {
    boolean canPass(LogEntry logEntry);
}
