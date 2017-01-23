package monik.services;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import monik.common.Checks;
import monik.logs.LogEntry;
import monik.logs.LogSeverity;
import monik.logs.LogUtils;
import monik.rabbitmq.Publisher;
import MonikPackage.nano.Monik;

public class MonikService extends LogcatToRabbitMqPublisher {

    private static final String EXTRA_MONIK_SOURCE   = "EXTRA_MONIK_SOURCE";
    private static final String EXTRA_MONIK_INSTANCE = "EXTRA_MONIK_INSTANCE";
    private static final String EXTRA_MIN_SEVERITY  = "EXTRA_MIN_SEVERITY";

    public static class Tags {
        public static final String SYSTEM       = "SYSTEM";
        public static final String APPLICATION  = "APPLICATION";
        public static final String LOGIC        = "LOGIC";
        public static final String SECURITY     = "SECURITY";
    }

    public static final class StartParams {
        public Publisher.Params rmqParams;
        public LogSeverity minSeverity;
        public String monikSource;
        public String monikInstance;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("rmq{");
            sb.append(rmqParams == null ? "null" : rmqParams.toString());
            sb.append("}");
            sb.append("; monikSource=" + monikSource );
            sb.append("; monikInstance=" + monikInstance);
            return sb.toString();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final Object mSync = new Object();
    private volatile String mMonikSource;
    private volatile String mMonikInstance;
    private volatile LogSeverity mMinSeverity;

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onLogEntry(LogEntry logEntry) {
        if (isPassedBySeverity(logEntry.severity)) {
            super.onLogEntry(logEntry);
        }
    }

    @Override
    protected byte[] logEntryToBytes(LogEntry logEntry) {

        try {
            String monikSource = null;
            String monikInstance = null;
            synchronized (mSync) {
                monikSource = mMonikSource;
                monikInstance = mMonikInstance;
            }

            int monikSeverity = severityToMonikSeverity(logEntry.severity);
            Integer monikLevel = tagToMonikLevel(logEntry.tag);
            if (monikLevel == null) {
                monikSeverity = Monik.VERBOSE;
                LogSeverity severity = LogSeverity.Verbose;
                if (logEntry.text != null && logEntry.text.startsWith("FATAL EXCEPTION:")) {
                    monikSeverity = Monik.FATAL;
                    severity = LogSeverity.Fatal;
                }
                if (!isPassedBySeverity(severity)) {
                    return null;
                }
                monikLevel = Monik.APPLICATION;
            }

            final Monik.Log monikLog = new Monik.Log();
            monikLog.level = monikLevel;
            monikLog.severity = monikSeverity;
            monikLog.format = Monik.PLAIN;
            monikLog.body = logEntry.text;
            monikLog.tags = logEntry.tag;

            final Monik.Event monikEvent = new Monik.Event();
            monikEvent.created = logEntry.date.getTime();
            monikEvent.source = monikSource;
            monikEvent.instance = monikInstance;
            monikEvent.setLg(monikLog);

            final byte[] bytes = new byte[monikEvent.getSerializedSize()];
            monikEvent.writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano.newInstance(bytes));

            return bytes;

        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize logEntry to monik event.", e);
        }
    }

    private boolean isPassedBySeverity(LogSeverity severity) {
        synchronized (mSync) {
            return severity.ordinal() >= mMinSeverity.ordinal();
        }
    }

    @Override
    protected void onBeforeStart(Intent intent) {
        handleIntent(intent);
        super.onBeforeStart(intent);
    }

    @Override
    protected void onCommand(Intent intent) {
        handleIntent(intent);
        super.onCommand(intent);
    }

    private void handleIntent(Intent intent) {

        final StringBuilder log = new StringBuilder();

        synchronized (mSync) {
            if (intent.hasExtra(EXTRA_MONIK_SOURCE)) {
                final String newVal = getMonikSource(intent);
                log.append("Monik source: " + mMonikSource + " -> " + newVal + LogUtils.getLineSeparator());
                mMonikSource = newVal;
            }

            if (intent.hasExtra(EXTRA_MONIK_INSTANCE)) {
                final String newVal = getMonikInstance(intent);
                log.append("Monik instance: " + mMonikInstance + " -> " + newVal + LogUtils.getLineSeparator());
                mMonikInstance = newVal;
            }

            if (intent.hasExtra(EXTRA_MIN_SEVERITY)) {
                final LogSeverity newVal = getMinSeverity(intent);
                log.append("Min severity: " + mMinSeverity + " -> " + newVal + LogUtils.getLineSeparator());
                mMinSeverity = newVal;
            }
        }

        if (log.length() > 0) {
            getLogger().i(Tags.APPLICATION, log.toString());
        }
    }

    public static void updateMonikInstance(Context context, String monikInstance) {
        Log.i(Tags.APPLICATION, "Update monik instance request: " + monikInstance);
        final Intent intent = new Intent(context, MonikService.class);
        setMonikInstance(intent, monikInstance);
        context.startService(intent);
    }

    public static void updateMonikSource(Context context, String monikSource) {
        Log.i(Tags.APPLICATION, "Update monik source request: " + monikSource);
        final Intent intent = new Intent(context, MonikService.class);
        MonikService.setMonikSource(intent, monikSource);
        context.startService(intent);
    }

    public static void start(Context context, StartParams startParams) {
        Log.i(Tags.APPLICATION, "Start request: " + startParams.toString());
        final Intent intent = new Intent(context, MonikService.class);
        LogcatToRabbitMqTextPublisher.setRabbitMqParams(intent, startParams.rmqParams);
        LogcatMonitor.setLogcatFilter(intent, severityToLogcatFilter(startParams.minSeverity));
        setMonikSource(intent, startParams.monikSource);
        setMonikInstance(intent, startParams.monikInstance);
        setMinSeverity(intent, startParams.minSeverity);
        context.startService(intent);
    }

    public static StartParams readStartParamsFromJsonAsset(Context context, String jsonAsset) {
        try {
            String strJson = null;
            final InputStream inputStream = context.getAssets().open(jsonAsset);
            try {
                byte[] buffer = new byte[inputStream.available()];
                final int read = inputStream.read(buffer);
                if (read != buffer.length) {
                    throw new IOException("Failed to read asset.");
                }
                strJson = new String(buffer, "UTF-8");
            } finally {
                inputStream.close();
            }

            JSONObject jsonMonik = null;
            JSONObject jsonRoot = new JSONObject(strJson);
            final JSONArray jsonLoggers = jsonRoot.getJSONArray("loggers");
            final int jsonLoggersCount = jsonLoggers.length();
            for (int i = 0; i < jsonLoggersCount; ++i) {
                final JSONObject jsonLogger = jsonLoggers.getJSONObject(i);
                final String channel = jsonLogger.getString("channel");
                if ("monik".equals(channel)) {
                    jsonMonik = jsonLogger;
                    break;
                }
            }

            Checks.checkArgNotNull(jsonMonik, "jsonMonik");
            final String severity = jsonMonik.getString("level").toLowerCase();
            final JSONObject monik = jsonMonik.getJSONObject("monik");
            final JSONObject sync = monik.getJSONObject("sync");
            final JSONObject mq = sync.getJSONObject("mq");
            final JSONObject meta = sync.getJSONObject("meta");
            final JSONObject async = monik.getJSONObject("async");

            final StartParams startParams = new StartParams();
            startParams.rmqParams = new Publisher.Params();
            startParams.rmqParams.uri = mq.optString("uri", startParams.rmqParams.uri);
            startParams.rmqParams.host = mq.optString("host", startParams.rmqParams.host);
            startParams.rmqParams.port = mq.optInt("port", startParams.rmqParams.port);
            startParams.rmqParams.useSsl = mq.optBoolean("useSsl", startParams.rmqParams.useSsl);
            startParams.rmqParams.user = mq.optString("user", startParams.rmqParams.user);
            startParams.rmqParams.password = mq.optString("password", startParams.rmqParams.password);
            startParams.rmqParams.exchange = mq.optString("exchange", startParams.rmqParams.exchange);
            if (async != null) {
                startParams.rmqParams.timeoutMilliseconds = async.optInt("retryTimeoutMillisecs", startParams.rmqParams.timeoutMilliseconds);
            }
            startParams.monikInstance = meta.optString("instance", null);
            startParams.monikSource = meta.optString("source", null);

            for (LogSeverity s : LogSeverity.values()) {
                if (s.name().toLowerCase().equals(severity)) {
                    startParams.minSeverity = s;
                    break;
                }
            }

            if (startParams.minSeverity == null) {
                throw new IllegalArgumentException("Unexpected severity: " + severity);
            }

            return startParams;

        } catch (Exception e) {
            throw new RuntimeException("Failed load StartParams from json asset.", e);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, MonikService.class));
    }

    public static void setMonikSource(Intent intent, String monikSource) {
        Checks.checkArgNotNull(monikSource, "monikSource");
        intent.putExtra(EXTRA_MONIK_SOURCE, monikSource);
    }

    public static String getMonikSource(Intent intent) {
        final String monikSource = intent.getStringExtra(EXTRA_MONIK_SOURCE);
        return Checks.checkArgNotNull(monikSource, EXTRA_MONIK_SOURCE);
    }

    public static void setMonikInstance(Intent intent, String monikInstance) {
        Checks.checkArgNotNull(monikInstance, "monikInstance");
        intent.putExtra(EXTRA_MONIK_INSTANCE, monikInstance);
    }

    public static String getMonikInstance(Intent intent) {
        final String monikInstance = intent.getStringExtra(EXTRA_MONIK_INSTANCE);
        return Checks.checkArgNotNull(monikInstance, EXTRA_MONIK_INSTANCE);
    }

    public static void setMinSeverity(Intent intent, LogSeverity severity) {
        Checks.checkArgNotNull(severity, "severity");
        LogUtils.writeSeverity(EXTRA_MIN_SEVERITY, intent, severity);
    }

    public static LogSeverity getMinSeverity(Intent intent) {
        final LogSeverity severity = LogUtils.readSeverity(EXTRA_MIN_SEVERITY, intent);
        return Checks.checkArgNotNull(severity, EXTRA_MIN_SEVERITY);
    }

    private static String severityToLogcatFilter(LogSeverity severity) {
        Checks.checkArgNotNull(severity, "severity");
        return "*:" + severity.name().substring(0, 1);
    }

    private static Integer tagToMonikLevel(String tag) {
        return TAGS_LEVELS_MAP.get(tag);
    }

    private static int severityToMonikSeverity(LogSeverity severity) {
        Integer monikSeverity = SEVERITIES_MAP.get(severity);
        return monikSeverity != null ? monikSeverity : Monik.VERBOSE;
    }

    private static final Map<String, Integer> TAGS_LEVELS_MAP = new HashMap<>(4);
    static {
        TAGS_LEVELS_MAP.put(Tags.SYSTEM      , Monik.SYSTEM);
        TAGS_LEVELS_MAP.put(Tags.APPLICATION , Monik.APPLICATION);
        TAGS_LEVELS_MAP.put(Tags.LOGIC       , Monik.LOGIC);
        TAGS_LEVELS_MAP.put(Tags.SECURITY    , Monik.SECURITY);
    }

    private static final Map<LogSeverity, Integer> SEVERITIES_MAP = new HashMap<>(4);
    static {
        SEVERITIES_MAP.put(LogSeverity.Debug   , Monik.VERBOSE);
        SEVERITIES_MAP.put(LogSeverity.Info    , Monik.INFO);
        SEVERITIES_MAP.put(LogSeverity.Warning , Monik.WARNING);
        SEVERITIES_MAP.put(LogSeverity.Error   , Monik.ERROR);
        SEVERITIES_MAP.put(LogSeverity.Assert  , Monik.VERBOSE);
        SEVERITIES_MAP.put(LogSeverity.Fatal   , Monik.FATAL);
    }
}
