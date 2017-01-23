package monik.logs;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

public final class LogEntry implements Parcelable {

    private static final class BundleKeys {
        private static final String DATE      = "date";
        private static final String PID       = "pid";
        private static final String TID       = "tid";
        private static final String SEVERITY  = "severity";
        private static final String TAG       = "tag";
        private static final String TEXT      = "text";
    }

    public Date date;
    public long pid;
    public long tid;
    public LogSeverity severity;
    public String tag;
    public String text;

    public LogEntry() {
    }

    public LogEntry(Bundle bundle) {
        date = (Date) bundle.getSerializable(BundleKeys.DATE);
        pid = bundle.getLong(BundleKeys.PID, pid);
        tid = bundle.getLong(BundleKeys.TID, tid);
        severity = LogUtils.readSeverity(BundleKeys.SEVERITY, bundle);
        tag = bundle.getString(BundleKeys.TAG, tag);
        text = bundle.getString(BundleKeys.TEXT, text);
    }

    public LogEntry(Parcel in) {
        date = (Date) in.readSerializable();
        pid = in.readLong();
        tid = in.readLong();
        severity = LogUtils.readSeverity(in);
        tag = in.readString();
        text = in.readString();
    }

    public Bundle toBundle() {
        final Bundle bundle = new Bundle();
        bundle.putSerializable(BundleKeys.DATE, date);
        bundle.putLong(BundleKeys.PID, pid);
        bundle.putLong(BundleKeys.TID, tid);
        LogUtils.writeSeverity(BundleKeys.SEVERITY, bundle, severity);
        bundle.putString(BundleKeys.TAG, tag);
        bundle.putString(BundleKeys.TEXT, text);
        return bundle;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeSerializable(date);
        out.writeLong(pid);
        out.writeLong(tid);
        LogUtils.writeSeverity(out, severity);
        out.writeString(tag);
        out.writeString(text);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<LogEntry> CREATOR = new Parcelable.Creator<LogEntry>() {
        @Override
        public final LogEntry createFromParcel(Parcel in) {
            return new LogEntry(in);
        }
        @Override public final LogEntry[] newArray(int size) {
            return new LogEntry[size];
        }
    };
}
