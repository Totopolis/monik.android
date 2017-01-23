package monik.common;

import android.util.Log;

public class AndroidLoggers {

    private AndroidLoggers() {
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class DefaultLogger implements Logger {
        @Override
        public void e(String tag, String msg) {
            Log.e(tag, msg);
        }

        @Override
        public void e(String tag, String msg, Throwable e) {
            Log.e(tag, msg, e);
        }

        @Override
        public void w(String tag, String msg) {
            Log.w(tag, msg);
        }

        @Override
        public void i(String tag, String msg) {
            Log.i(tag, msg);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class DebugLogger implements Logger {
        @Override
        public void e(String tag, String msg) {
            Log.d(tag, "E: " + msg);
        }

        @Override
        public void e(String tag, String msg, Throwable e) {
            Log.d(tag, "E: " + msg + (e != null ? Log.getStackTraceString(e) : ""));
        }

        @Override
        public void w(String tag, String msg) {
            Log.d(tag, "W: " + msg);
        }

        @Override
        public void i(String tag, String msg) {
            Log.d(tag, "I: " + msg);
        }
    }
}
