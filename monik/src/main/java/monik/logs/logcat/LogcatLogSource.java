package monik.logs.logcat;

import android.os.Process;
import android.support.annotation.Nullable;

import monik.common.Checks;
import monik.common.Logger;
import monik.common.RetryException;
import monik.logs.LogConsumer;
import monik.logs.LogEntry;
import monik.logs.LogFilter;
import monik.logs.LogSource;
import monik.logs.LogUtils;

import java.util.ArrayList;
import java.util.List;

public class LogcatLogSource implements LogSource {

    private static final String LOG_TAG = "LogcatLogSource";
    private static final int MAX_LOG_LINES = 128;

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static LogFilter skipThisPid() {
        final long thisPid = Process.myPid();
        return new LogFilter() {
            @Override
            public boolean canPass(LogEntry logEntry) {
                return thisPid != logEntry.pid;
            }
        };
    }

    private static LogFilter skipFilteringTid() {
        return new LogFilter() {
            @Override
            public boolean canPass(LogEntry logEntry) {
                return Process.myTid() != logEntry.tid;
            }
        };
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public enum PidTidFilter {

        Pid(skipThisPid()),
        Tid(skipFilteringTid());

        PidTidFilter(LogFilter filter) {
            this.filter = filter;
        }

        LogFilter filter;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static final class Source {

        private final int mMaxLogLines = MAX_LOG_LINES;
        private final Logger mLogger;
        private final LogConsumer mLogConsumer;
        private final LogcatReader mLogcatReader;
        private final List<String> mLogLines;
        private final StringBuilder mTextBuffer;

        public Source(String logcatFilter, Logger logger, final LogConsumer logConsumer) {
            mLogger = Checks.checkArgNotNull(logger, "logger");
            mLogConsumer = Checks.checkArgNotNull(logConsumer, "logConsumer");
            mLogLines = new ArrayList<>();
            mTextBuffer = new StringBuilder();
            mLogcatReader = new LogcatReader(logcatFilter, logger, new LogcatReader.Output() {
                @Override
                public void writeLine(String line) {
                    consumeLine(line, false);
                }

                @Override
                public void flush() {
                    consumeLine(null, true);
                }
            });
        }

        void start() {
            mLogcatReader.start();
        }

        public void close() {
            mLogConsumer.close();
            mLogcatReader.close();
        }

        private void consumeLine(@Nullable String line, boolean flushAnyway) {

            if (LogcatLinesParser.isBeginOfLog(line)) {
                try {
                    flushLogLines();
                    mLogLines.clear();
                    mLogLines.add(line);
                } catch (RetryException e) {
                    throw e;
                } catch (Exception e) {
                    mLogLines.clear();
                    mLogLines.add(line);
                }
                return;
            }

            if (mLogLines.isEmpty()) {
                // There is no 'beginOfLog' line.
                return;
            }

            if (line != null) {
                if (mLogLines.size() >= mMaxLogLines - 1) {
                    line += "...";
                    flushAnyway = true;
                }
                mLogLines.add(line);
            }

            if (flushAnyway) {
                try {
                    flushLogLines();
                    mLogLines.clear();
                } catch (RetryException e) {
                    if (line != null) {
                        mLogLines.remove(mLogLines.size() - 1);
                    }
                    throw e;
                } catch (Exception e) {
                    mLogLines.clear();
                    throw e;
                }
            }
        }

        private void flushLogLines() {
            LogEntry logEntry = null;
            try {
                logEntry = LogcatLinesParser.parseLogLines(mLogLines, mTextBuffer);
            } catch (Exception e) {
                mLogger.e(LOG_TAG, "Failed to parse log lines.", e);
                mTextBuffer.setLength(0);
                mTextBuffer.append("Bad lines [");
                mTextBuffer.append(mLogLines.size());
                mTextBuffer.append("]: { ");
                final String lineSeparator = LogUtils.getLineSeparator();
                int idx = 0;
                for (int end = Math.min(mLogLines.size(), 10); idx < end; ++idx) {
                    mTextBuffer.append(lineSeparator);
                    mTextBuffer.append("#");
                    mTextBuffer.append(idx);
                    mTextBuffer.append(": ");
                    mTextBuffer.append(mLogLines.get(idx));
                }
                mTextBuffer.append(lineSeparator);
                mTextBuffer.append((idx < mLogLines.size()) ? ("... }") : "}");
                mLogger.e(LOG_TAG, mTextBuffer.toString());
                throw e;
            }
            if (logEntry != null) {
                mLogConsumer.consume(logEntry);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final String mLogcatFilter;
    private final PidTidFilter mPidTidFilter;
    private final Logger mLogger;
    private Source mSource;

    public LogcatLogSource(String logcatFilter,
                           PidTidFilter pidtidFilter,
                           Logger logger) {
        mLogcatFilter = Checks.checkArgNotNull(logcatFilter, "logcatFilter");
        mPidTidFilter =  Checks.checkArgNotNull(pidtidFilter, "pidTidFilter");
        mLogger = Checks.checkArgNotNull(logger, "logger");
    }

    @Override
    public void start(LogConsumer consumer) {
        if (mSource != null) {
            throw new IllegalStateException("Multiple start is not supported.");
        }
        mSource = new Source(
                mLogcatFilter,
                mLogger,
                LogUtils.makeFiltering(consumer, mPidTidFilter.filter));
        mSource.start();
    }

    @Override
    public void close() {
        if (mSource != null) {
            mSource.close();
        }
    }
}
