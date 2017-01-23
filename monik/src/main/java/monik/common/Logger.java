package monik.common;

public interface Logger {
    void e(String tag, String msg);
    void e(String tag, String msg, Throwable e);
    void w(String tag, String msg);
    void i(String tag, String msg);
}
