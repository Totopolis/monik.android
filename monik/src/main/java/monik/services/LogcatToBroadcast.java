package monik.services;

import android.content.Intent;

import monik.common.Checks;
import monik.logs.LogEntry;

public abstract class LogcatToBroadcast extends LogcatMonitor {

    private static final String EXTRA_BROADCAST_ACTION = "EXTRA_BROADCAST_ACTION";
    private static final String EXTRA_LOG_ENTRY = "EXTRA_LOG_ENTRY";

    private String mBroadcastAction;

    @Override
    protected void onBeforeStart(Intent intent) {
        mBroadcastAction = Checks.checkArgNotNull(getBroadcastAction(intent), "broadcastAction");
    }

    @Override
    protected void onLogEntry(LogEntry logEntry) {
        final Intent intent = new Intent(mBroadcastAction);
        setLogEntry(intent, logEntry);
        getApplicationContext().sendBroadcast(intent);
    }

    public static void setBroadcastAction(Intent intent, String broadcastAction) {
        intent.putExtra(EXTRA_BROADCAST_ACTION, broadcastAction);
    }

    public static String getBroadcastAction(Intent intent) {
        return intent.getStringExtra(EXTRA_BROADCAST_ACTION);
    }

    public static void setLogEntry(Intent intent, LogEntry logEntry) {
        intent.putExtra(EXTRA_LOG_ENTRY, logEntry);
    }

    public static LogEntry getLogEntry(Intent intent) {
        return (LogEntry) intent.getParcelableExtra(EXTRA_LOG_ENTRY);
    }
}
