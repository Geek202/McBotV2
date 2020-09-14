package me.geek.tom.mcbot.commands.api;

import me.geek.tom.mcbot.McBot;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

public interface ICommand<T extends CommandArgs> {

    Mono<?> handle(CommandContext<T> ctx);

    default List<ICommand<?>> subcommands() {
        return Collections.emptyList();
    }
    String getName();

    T createArgs(McBot mcBot);
    boolean hasArgs();

    default void onBotStarting(McBot mcBot) { }
    default void onBotStopping(McBot mcBot) { }
}
