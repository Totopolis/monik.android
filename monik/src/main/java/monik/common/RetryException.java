package monik.common;

public class RetryException extends RuntimeException {

    private static final long DEFAULT_MIN_TIMEOUT_MILLISECONDS = 500;

    private final long mMinTimeoutMilliseconds;

    public RetryException() {
        super();
        mMinTimeoutMilliseconds = DEFAULT_MIN_TIMEOUT_MILLISECONDS;
    }

    public RetryException(long minTimeoutMilliseconds) {
        super();
        mMinTimeoutMilliseconds = minTimeoutMilliseconds;
    }

    public RetryException(String msg) {
        super(msg);
        mMinTimeoutMilliseconds = DEFAULT_MIN_TIMEOUT_MILLISECONDS;
    }

    public RetryException(long minTimeoutMilliseconds, String msg) {
        super(msg);
        mMinTimeoutMilliseconds = minTimeoutMilliseconds;
    }

    public RetryException(String msg, Throwable cause) {
        super(msg, cause);
        mMinTimeoutMilliseconds = DEFAULT_MIN_TIMEOUT_MILLISECONDS;
    }

    public RetryException(long minTimeoutMilliseconds, String msg, Throwable cause) {
        super(msg, cause);
        mMinTimeoutMilliseconds = minTimeoutMilliseconds;
    }

    public long getMinTimeoutMilliseconds() {
        return mMinTimeoutMilliseconds;
    }
}
