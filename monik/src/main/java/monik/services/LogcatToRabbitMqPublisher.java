package monik.services;

import android.content.Intent;

import monik.common.Checks;
import monik.logs.LogEntry;
import monik.rabbitmq.Publisher;

public abstract class LogcatToRabbitMqPublisher extends LogcatMonitor {

    private static final String EXTRA_RABBITMQ_PARAMS = "EXTRA_RABBITMQ_PARAMS";

    private Publisher mPublisher;

    protected abstract byte[] logEntryToBytes(LogEntry logEntry);

    @Override
    protected void onBeforeStart(Intent intent) {
        final Publisher.Params rabbitMqParams = Checks.checkArgNotNull(getRabbitMqParams(intent), "rabbitMqParams");
        mPublisher = new Publisher(getLogger(), rabbitMqParams);
    }

    @Override
    public void onDestroy() {
        if (mPublisher != null) {
            mPublisher.close();
        }
        super.onDestroy();
    }

    @Override
    protected void onLogEntry(LogEntry logEntry) {
        final byte[] data = logEntryToBytes(logEntry);
        if (data != null) {
            mPublisher.publish(data);
        }
    }

    public static void setRabbitMqParams(Intent intent, Publisher.Params params) {
        intent.putExtra(EXTRA_RABBITMQ_PARAMS, params.toBundle());
    }

    public static Publisher.Params getRabbitMqParams(Intent intent) {
        return intent.hasExtra(EXTRA_RABBITMQ_PARAMS)
             ? new Publisher.Params(intent.getBundleExtra(EXTRA_RABBITMQ_PARAMS))
             : null;
    }
}
