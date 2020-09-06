package me.geek.tom.mcbot.logging;

@SuppressWarnings("unused")
public interface ISentryProxy {
    void init(String dsn);
    void handleUnexpectedError(Throwable t, String commandInput, String context);

    void logGuildCount(int count);
    void logBotStop();
}
