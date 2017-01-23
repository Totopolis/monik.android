package monik.logs;

public interface LogSource {
    void start(LogConsumer consumer);
    void close();
}
