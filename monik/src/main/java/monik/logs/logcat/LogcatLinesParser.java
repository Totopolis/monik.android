package monik.logs.logcat;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import monik.logs.LogEntry;
import monik.logs.LogSeverity;
import monik.logs.LogUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

class LogcatLinesParser {

    // Matched sample: [ 12-27 19:08:17.523 26172:26172 E/SomeTag ]
    private static final String START_LOG_REGEXP = ""
            + "^\\["
            + "\\s*\\d{1,2}-\\d{1,2}"
            + "\\s+\\d{1,2}:\\d{1,2}:\\d{1,2}.\\d{1,3}"
            + "\\s+\\d+\\s*:\\s*\\d+"
            + "\\s+[VDIWEAFvdiweaf]\\/.*"
            + "\\s*\\]$";

    private static final Pattern START_LOG_PATTERN = Pattern.compile(START_LOG_REGEXP);
    private static final String LINE_SEPARATOR = LogUtils.getLineSeparator();
    private static final String LOG_LEVELS = "VDIWEFAvdiwefa";

    public static String getFormatArg() {
        return "-v long";
    }

    public static @Nullable LogEntry parseLogLines(@NonNull List<String> logLines, @Nullable StringBuilder buffer) {

        if (logLines.size() < 2) { // beginOfLog + text
            return null;
        }

        try {
            final LogEntry logEntry = new LogEntry();
            final String beginOfLog = logLines.get(0);
            final String[] words = splitBeginOfLog(beginOfLog);
            logEntry.date = logWordsToDate(words);
            logEntry.pid = logWordsToPid(words);
            logEntry.tid = logWordsToTid(words);
            logEntry.severity = logWordsToSeverity(words);
            logEntry.tag = logWordsToTag(words);

            if (buffer == null) {
                buffer = new StringBuilder();
            }
            buffer.setLength(0);
            buffer.append(logLines.get(1));
            for (int i = 2, end = logLines.size(); i < end; ++i) {
                buffer.append(LINE_SEPARATOR);
                buffer.append(logLines.get(i));
            }

            logEntry.text = buffer.toString();

            return logEntry;
        } catch (ParseException e) {
            throw new RuntimeException("Failed to parse log lines.", e);
        }
    }

    public static boolean isBeginOfLog(@Nullable String line) {
        return line != null && START_LOG_PATTERN.matcher(line).matches();
    }

    private static String[] splitBeginOfLog(@NonNull String beginOfLog) {
        beginOfLog = beginOfLog.trim().substring(1, beginOfLog.length() - 2).trim(); // remove []
        return  beginOfLog.split("\\s+");
    }

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    private static Date logWordsToDate(@NonNull String[] logWords) throws ParseException {
        final Calendar calendar = Calendar.getInstance();
        final String strDate = calendar.get(Calendar.YEAR) + "-" + logWords[0] + " " + logWords[1];
        Date date = DATE_FORMAT.parse(strDate);
        // Date in logs is without year. So we have to check edge case: current month is january, log date is december.
        final int currentMonth = calendar.get(Calendar.MONTH);
        calendar.setTime(date);
        final int dateMonth = calendar.get(Calendar.MONTH);
        if (dateMonth > currentMonth) {
            calendar.add(Calendar.YEAR, -1);
            date = calendar.getTime();
        }
        return date;
    }

    private static long logWordsToPid(@NonNull String[] logWords) {
        String strPid = logWords[2];
        final int pos = strPid.indexOf(':');
        strPid = strPid.substring(0, pos);
        return Long.parseLong(strPid);
    }

    private static long logWordsToTid(@NonNull String[] logWords) {
        String strTid = logWords[2];
        final int pos = strTid.indexOf(':');
        if (pos != strTid.length() - 1) {
            strTid = strTid.substring(pos + 1);
        } else {
            strTid = logWords[3];
        }
        return Long.parseLong(strTid);
    }

    private static LogSeverity logWordsToSeverity(@NonNull String[] logWords) {
        final int idx = getLogLevelIndex(logWords);
        switch (logWords[idx].charAt(0)) {
            case 'V': case 'v':
                return LogSeverity.Verbose;

            case 'D': case 'd':
                return LogSeverity.Debug;

            case 'I': case 'i':
                return LogSeverity.Info;

            case 'W': case 'w':
                return LogSeverity.Warning;

            case 'E': case 'e':
                return LogSeverity.Error;

            case 'A': case 'a':
                return LogSeverity.Assert;

            case 'F': case 'f':
                return LogSeverity.Fatal;
        }
        throw new RuntimeException("Unexpected log level.");
    }

    private static String logWordsToTag(@NonNull String[] logWords) {
        final int idx = getLogLevelIndex(logWords);
        String tag = logWords[idx].substring(2);
        for (int i = idx + 1; i < logWords.length; ++i) {
            tag += ' ' + logWords[i];
        }
        return tag;
    }

    private static int getLogLevelIndex(@NonNull String[] logWords) {
        int idx = 3;
        if (logWords[idx].charAt(1) != '/') {
            idx = 4;
            if (logWords[idx].charAt(1) != '/') {
                throw new IllegalArgumentException("Bad log words.");
            }
        }
        if (LOG_LEVELS.indexOf(logWords[idx].charAt(0)) == -1) {
            throw new IllegalArgumentException("Bad log words.");
        }
        return idx;
    }
}
