package monik.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import monik.common.AndroidLoggers;
import monik.common.Checks;
import monik.common.Logger;
import monik.logs.LogConsumer;
import monik.logs.LogEntry;
import monik.logs.LogSource;
import monik.logs.logcat.LogcatLogSource;

public abstract class LogcatMonitor extends Service {

    private static final String EXTRA_LOGCAT_LAST_LOGS_COUNT = "EXTRA_LOGCAT_LAST_LOGS_COUNT";
    private static final int DEFAULT_LOGCAT_LAST_LOGS_COUNT = -1;

    private static final String EXTRA_LOGCAT_FILTER = "EXTRA_LOGCAT_FILTER";
    private static final String DEFAULT_LOGCAT_FILTER = "*:I";

    private static final String EXTRA_PIDTID_FILTER = "EXTRA_PIDTID_FILTER";
    private static final LogcatLogSource.PidTidFilter DEFAULT_PIDTID_FILTER = LogcatLogSource.PidTidFilter.Pid;

    private Logger mLogger;
    private LogConsumer mLogConsumer;
    private LogSource mLogSource;

    protected abstract void onLogEntry(LogEntry logEntry);

    protected void onBeforeStart(Intent intent) {
    }

    protected void onCommand(Intent intent) {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mLogger = new AndroidLoggers.DebugLogger();
        mLogConsumer = new LogConsumer() {
            @Override
            public void consume(LogEntry logEntry) {
                onLogEntry(logEntry);
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            return START_STICKY;
        }

        if (mLogSource != null) {
            onCommand(intent);
            return START_REDELIVER_INTENT;
        }

        onBeforeStart(intent);
        mLogSource = new LogcatLogSource(
                getLogcatLastLogsCount(intent, DEFAULT_LOGCAT_LAST_LOGS_COUNT),
                getLogcatFilter(intent, DEFAULT_LOGCAT_FILTER),
                getPidTidFilter(intent, DEFAULT_PIDTID_FILTER),
                mLogger);
                mLogSource.start(mLogConsumer);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mLogSource != null) {
            mLogSource.close();
        }
        super.onDestroy();
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return null;
    }

    protected final Logger getLogger() {
        return mLogger;
    }

    public static void setLogcatLastLogsCount(Intent intent, int logcatLastLogsCount) {
        intent.putExtra(EXTRA_LOGCAT_LAST_LOGS_COUNT, logcatLastLogsCount);
    }

    public static int getLogcatLastLogsCount(Intent intent, int defaultLogcatLastLogsCount) {
        return intent.getIntExtra(EXTRA_LOGCAT_LAST_LOGS_COUNT, defaultLogcatLastLogsCount);
    }

    public static void setLogcatFilter(Intent intent, String filter) {
        Checks.checkArgNotNull(filter, "filter");
        intent.putExtra(EXTRA_LOGCAT_FILTER, filter);
    }

    public static String getLogcatFilter(Intent intent, String defaultFilter) {
        Checks.checkArgNotNull(defaultFilter, "defaultFilter");
        return intent.hasExtra(EXTRA_LOGCAT_FILTER)
             ? intent.getStringExtra(EXTRA_LOGCAT_FILTER)
             : defaultFilter;
    }

    public static void setPidTidFilter(Intent intent, LogcatLogSource.PidTidFilter filter) {
        Checks.checkArgNotNull(filter, "filter");
        intent.putExtra(EXTRA_LOGCAT_FILTER, filter.ordinal());
    }

    public static LogcatLogSource.PidTidFilter getPidTidFilter(Intent intent, LogcatLogSource.PidTidFilter defaultFilter) {
        Checks.checkArgNotNull(defaultFilter, "defaultFilter");
        return LogcatLogSource.PidTidFilter.values()[intent.getIntExtra(EXTRA_PIDTID_FILTER, defaultFilter.ordinal())];
    }
}
