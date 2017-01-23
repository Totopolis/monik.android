package monik.logs;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;

import monik.common.Checks;

public class LogUtils {

    private static final String LINE_SEPARATOR = getLineSeparator();

    private LogUtils() {
    }

    public static String toText(LogEntry logEntry) {
        if (logEntry == null) {
            return "" + logEntry;
        }
        StringBuilder log = new StringBuilder();
        log.append( "date: '" + logEntry.date     + "'").append(LINE_SEPARATOR);
        log.append(  "pid: '" + logEntry.pid      + "'").append(LINE_SEPARATOR);
        log.append(  "tid: '" + logEntry.tid      + "'").append(LINE_SEPARATOR);
        log.append("level: '" + logEntry.severity + "'").append(LINE_SEPARATOR);
        log.append(  "tag: '" + logEntry.tag      + "'").append(LINE_SEPARATOR);
        log.append( "text: '" + logEntry.text     + "'");
        return log.toString();
    }

    public static LogConsumer makeFiltering(final LogConsumer consumer, final LogFilter filter) {
        Checks.checkArgNotNull(consumer, "consumer");
        Checks.checkArgNotNull(filter, "filter");
        return new LogConsumer() {
            @Override
            public void consume(LogEntry logEntry) {
                if (filter.canPass(logEntry)) {
                    consumer.consume(logEntry);
                }
            }

            @Override
            public void close() {
                consumer.close();
            }
        };
    }

    public static String getLineSeparator() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                ? System.lineSeparator()
                : "\n";
    }

    public static LogSeverity readSeverity(Parcel in) {
        final int idx = in.readInt();
        return  idx == -1 ? null : LogSeverity.values()[idx];
    }

    public static void writeSeverity(Parcel out, LogSeverity severity) {
        out.writeInt(severity == null ? -1 : severity.ordinal());
    }

    public static LogSeverity readSeverity(String key, Bundle bundle) {
        final int idx = bundle.getInt(key);
        return idx == -1 ? null : LogSeverity.values()[idx];
    }

    public static void writeSeverity(String key, Bundle bundle, LogSeverity severity) {
        bundle.putInt(key, severity == null ? -1 : severity.ordinal());
    }

    public static LogSeverity readSeverity(String key, Intent intent) {
        final int idx = intent.getIntExtra(key, -1);
        return idx == -1 ? null : LogSeverity.values()[idx];
    }

    public static void writeSeverity(String key, Intent intent, LogSeverity severity) {
        intent.putExtra(key, severity == null ? -1 : severity.ordinal());
    }
}
