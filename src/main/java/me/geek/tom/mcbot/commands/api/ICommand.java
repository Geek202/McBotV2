package me.geek.tom.mcbot.commands.api;

import me.geek.tom.mcbot.McBot;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

public interface ICommand<T extends CommandArgs> {

    Mono<?> handle(CommandContext<T> ctx);
    T createArgs(McBot mcBot);

    default List<ICommand<?>> subcommands() {
        return Collections.emptyList();
    }

    String getName();

    boolean hasArgs();

}
