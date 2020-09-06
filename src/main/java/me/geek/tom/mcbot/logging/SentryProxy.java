package me.geek.tom.mcbot.logging;

import io.sentry.SentryClient;
import io.sentry.SentryClientFactory;

import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unused")
public class SentryProxy implements ISentryProxy {

    private final AtomicReference<SentryClient> client;

    public SentryProxy() {
        client = new AtomicReference<>();
    }

    @Override
    public void init(String dsn) {
        client.set(SentryClientFactory.sentryClient(dsn));
    }

    @Override
    public void handleUnexpectedError(Throwable t, String commandInput, String context) {
        synchronized (client) {
            client.get().getContext().addExtra("commandInput", commandInput);
            client.get().getContext().addExtra("additional", context);
            client.get().sendException(t);
            client.get().getContext().clearExtra();
        }
    }

    @Override
    public void logGuildCount(int count) {
        synchronized (client) {
            client.get().getContext().addExtra("Guild count", count);
            client.get().sendMessage("Bot online!");
            client.get().getContext().clearExtra();
        }
    }

    @Override
    public void logBotStop() {
        synchronized (client) {
            client.get().sendMessage("Bot shutdown and disconnected!");
        }
    }
}
