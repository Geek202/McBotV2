package me.geek.tom.mcbot.logging;

@SuppressWarnings("unused")
public class NoopSentryProxy implements ISentryProxy {
    @Override
    public void init(String dsn) { }

    @Override
    public void handleUnexpectedError(Throwable t, String commandInput, String context) { }

    @Override
    public void logGuildCount(int count) { }

    @Override
    public void logBotStop() { }
}
