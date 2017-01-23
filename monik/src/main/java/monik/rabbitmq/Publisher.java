package monik.rabbitmq;

import android.os.Bundle;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.StrictMode;

import monik.common.Checks;
import monik.common.Logger;
import monik.common.RetryException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public final class Publisher {

    private static final String LOG_TAG = "RabbitMqPublisher";

    public static final class Params implements Parcelable {

        private static final class BundleKeys {
            private static final String URI                  = "uri";
            private static final String HOST                 = "host";
            private static final String PORT                 = "port";
            private static final String USE_SSL              = "useSsl";
            private static final String USER                 = "user";
            private static final String PASSWORD             = "password";
            private static final String EXCHANGE             = "exchange";
            private static final String TIMEOUT_MILLISECONDS = "timeoutMilliseconds";
        }

        public String uri;
        public String host;
        public int port = ConnectionFactory.DEFAULT_AMQP_PORT;
        public boolean useSsl = false;
        public String user;
        public String password;
        public String exchange;
        public int timeoutMilliseconds = 10000;

        public Params() {
        }

        public Params(Bundle bundle) {
            uri                 = bundle.getString (BundleKeys.URI                  , uri);
            host                = bundle.getString (BundleKeys.HOST                 , host);
            port                = bundle.getInt    (BundleKeys.PORT                 , port);
            useSsl              = bundle.getBoolean(BundleKeys.USE_SSL              , useSsl);
            user                = bundle.getString (BundleKeys.USER                 , user);
            password            = bundle.getString (BundleKeys.PASSWORD             , password);
            exchange            = bundle.getString (BundleKeys.EXCHANGE             , exchange);
            timeoutMilliseconds = bundle.getInt    (BundleKeys.TIMEOUT_MILLISECONDS , timeoutMilliseconds);
        }

        public Params(Parcel in) {
            uri                 = in.readString();
            host                = in.readString();
            port                = in.readInt();
            useSsl              = in.readByte() != 0;
            user                = in.readString();
            password            = in.readString();
            exchange            = in.readString();
            timeoutMilliseconds = in.readInt();
        }

        public Bundle toBundle() {
            final Bundle bundle = new Bundle();
            bundle.putString (BundleKeys.URI                  , uri);
            bundle.putString (BundleKeys.HOST                 , host);
            bundle.putInt    (BundleKeys.PORT                 , port);
            bundle.putBoolean(BundleKeys.USE_SSL              , useSsl);
            bundle.putString (BundleKeys.USER                 , user);
            bundle.putString (BundleKeys.PASSWORD             , password);
            bundle.putString (BundleKeys.EXCHANGE             , exchange);
            bundle.putInt    (BundleKeys.TIMEOUT_MILLISECONDS , timeoutMilliseconds);
            return bundle;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeString(uri);
            out.writeString(host);
            out.writeInt(port);
            out.writeByte((byte)(useSsl ? 1 : 0));
            out.writeString(user);
            out.writeString(password);
            out.writeString(exchange);
            out.writeString(exchange);
            out.writeInt(timeoutMilliseconds);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<Params> CREATOR = new Parcelable.Creator<Params>() {
            @Override
            public final Params createFromParcel(Parcel in) {
                return new Params(in);
            }
            @Override public final Params[] newArray(int size) {
                return new Params[size];
            }
        };

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("uri=" + uri);
            sb.append("; host=" + host);
            sb.append("; port=" + port);
            sb.append("; useSsl=" + useSsl);
            sb.append("; user=" + user);
            sb.append("; password=" + password);
            sb.append("; exchange=" + exchange);
            sb.append("; timeoutMilliseconds=" + timeoutMilliseconds);
            return sb.toString();
        }
    }

    private final Object mSync = new Object();
    private final Logger mLogger;
    private final Params mParams;
    private final ConnectionFactory mConnectionFactory;
    private volatile boolean mClosed = false;
    private volatile Channel mChannel;

    public Publisher(Logger logger, Params params) {
        mLogger = Checks.checkArgNotNull(logger, "logger");
        mParams = Checks.checkArgNotNull(params, "params");
        mLogger.i(LOG_TAG, "Params: " + mParams.toString());

        mConnectionFactory = new ConnectionFactory();
        if (mParams.uri != null) {
            try {
                mConnectionFactory.setUri(mParams.uri);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set uri: " + mParams.uri, e);
            }
        } else {
            mConnectionFactory.setHost(mParams.host);
            mConnectionFactory.setPort(mParams.port);
            mConnectionFactory.setUsername(mParams.user);
            mConnectionFactory.setPassword(mParams.password);
            if (mParams.useSsl) {
                try {
                    mConnectionFactory.useSslProtocol();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to use SSL protocol.", e);
                }
            }
        }
        mConnectionFactory.setConnectionTimeout(mParams.timeoutMilliseconds);
        mConnectionFactory.setAutomaticRecoveryEnabled(false);
    }

    public final void publish(byte[] data) {
        Checks.checkArgNotNull(data, "data");
        try {
            final Channel channel = ensureConnected();
            channel.basicPublish(mParams.exchange, "", null, data);
        } catch (ClosedException e) {
            throw e;
        } catch (Exception e) {
            close(false);
            throw new RetryException(mParams.timeoutMilliseconds, "Failed to publish data.", e);
        }
    }

    public final void close() {
        close(true);
    }

    private void close(boolean forever) {
        Connection connection = null;
        synchronized (mSync) {
            mClosed |= forever;
            if (mChannel != null) {
                connection = mChannel.getConnection();
                mChannel = null;
            }
        }
        if (connection != null) {
            try {
                if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
                    connection.close(mParams.timeoutMilliseconds);
                } else {
                    StrictMode.ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
                    try {
                        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder(oldPolicy).permitNetwork().build());
                        connection.close(mParams.timeoutMilliseconds);
                    } finally {
                        StrictMode.setThreadPolicy(oldPolicy);
                    }
                }
            } catch (Exception e) {
                mLogger.e(LOG_TAG, "Failed to close connection.", e);
            }
        }
    }

    private Channel ensureConnected() throws IOException, TimeoutException {
        synchronized (mSync) {
            if (mClosed) {
                throw new IllegalStateException("Publisher is closed.");
            }
            if (mChannel == null) {
                final Connection connection = mConnectionFactory.newConnection();
                mChannel = connection.createChannel();
            }
            return mChannel;
        }
    }

    private static class ClosedException extends IllegalStateException {
        public ClosedException() {
            super("Publisher is closed.");
        }
    }
}
