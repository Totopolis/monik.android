package monik.logs.logcat;

import android.text.TextUtils;
import android.util.Log;

import monik.common.Checks;
import monik.common.Logger;
import monik.common.Refs;
import monik.common.RetryException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Process;
import java.util.ArrayList;

class LogcatReader {

    private static final String LOG_TAG = "LogcatReader";

    private static final long WAIT_TIMEOUT = 500;

    public interface Output {
        void writeLine(String line);
        void flush();
    }

    private final Object mSync = new Object();
    private final String mFilter;
    private final Logger mLogger;
    private final Output mOutput;
    private volatile Boolean mCloseRequested = false;
    private volatile Process mProcess;
    private volatile Thread mThread;

    public LogcatReader(String filter, Logger logger, Output output) {
        mFilter = Checks.checkArgNotNull(filter, "filter");
        mLogger = Checks.checkArgNotNull(logger, "logger");
        mOutput = Checks.checkArgNotNull(output, "output");
    }

    public void start() {

        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                threadFunc();
            }
        });

        synchronized (mSync) {
            if (mCloseRequested) {
                throw new IllegalStateException("Close has already already requested.");
            }
            if (mProcess != null) {
                throw new IllegalStateException("Multiple start is not supported.");
            }
            mProcess = startLogcat();
            mThread = thread;
            mThread.start();
        }
    }

    public void close() {

        Process process = null;
        Thread thread = null;

        synchronized (mSync) {
            process = mProcess;
            thread = mThread;
            mCloseRequested = true;
        }

        if (process != null) {
            process.destroy();
        }

        if (thread != null) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // Ignore.
            }
        }
    }

    private void threadFunc() {

        try {

            final InputStream processStream = mProcess.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(processStream));

            final Refs.Ref<String> lineRef = new Refs.Ref<>();

            final Runnable writeLineAction = new Runnable() {
                @Override
                public void run() {
                    mOutput.writeLine(lineRef.obj);
                    lineRef.obj = null;
                }
            };
            final Runnable flushAction = new Runnable() {
                @Override
                public void run() {
                    mOutput.flush();
                }
            };

            Runnable outputAction = null;

            mLogger.i(LOG_TAG, "Logcat reading has been started.");

            while (!isCloseRequested()) {

                if (outputAction != null) {
                    try {
                        outputAction.run();
                        outputAction = null;
                    } catch (RetryException e) {
                        final long timeout = e.getMinTimeoutMilliseconds();
                        mLogger.e(LOG_TAG, "RetryException [" + timeout + " ms]: " + Log.getStackTraceString(e));
                        sleep(timeout);
                    } catch (Exception e) {
                        mLogger.e(LOG_TAG, "Exception: " + Log.getStackTraceString(e));
                        outputAction = null;
                    }
                    continue;
                }

                if (!bufferedReader.ready()) {
                    sleep(WAIT_TIMEOUT);
                    if (!bufferedReader.ready()) {
                        outputAction = flushAction;
                        continue;
                    }
                }

                lineRef.obj = bufferedReader.readLine();
                if (lineRef.obj != null) {
                    outputAction = writeLineAction;
                    continue;
                }

                break;
            }

            mLogger.i(LOG_TAG, "Logcat reading has been finished.");

        } catch (Exception e) {
            mLogger.e(LOG_TAG, "Logcat reading has been failed: " + Log.getStackTraceString(e));
            throw new RuntimeException("Logcat reading has been failed.", e);
        }
    }

    private boolean isCloseRequested() {
        synchronized (mSync) {
            return mCloseRequested;
        }
    }

    private void sleep(long timeout) throws InterruptedException {
        // TODO: break sleeping if 'close' has been invoked.
        Thread.sleep(timeout);
        Thread.yield();
    }

    private Process startLogcat() {
        ArrayList<String> args = new ArrayList<>();
        args.add("logcat");
        args.add(LogcatLinesParser.getFormatArg());
        args.add("-T 0"); // last 0 lines
        args.add(mFilter);
        final String command = TextUtils.join(" ", args.toArray(new String[args.size()]));
        try {
            return Runtime.getRuntime().exec(command);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to start logcat (" + command + ").", e);
        }
    }
}
