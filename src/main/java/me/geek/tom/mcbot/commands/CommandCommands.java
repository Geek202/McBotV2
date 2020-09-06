package me.geek.tom.mcbot.commands;

import discord4j.rest.util.Color;
import me.geek.tom.mcbot.McBot;
import me.geek.tom.mcbot.commands.api.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Command
public class CommandCommands implements ICommand<CommandArgs> {
    @Override
    public Mono<?> handle(CommandContext<CommandArgs> ctx) {
        return Mono.just(ctx.getManager().getCommands())
                .flatMapIterable(Map::keySet)
                .map(s -> "`" + CommandManager.COMMAND_PREFIX + s + "`")
                .collectList()
                .zipWith(ctx.getChannel(), (commands, channel) -> channel.createEmbed(embed -> embed
                        .setTitle("Available commands: ")
                        .setDescription(String.join("\n", commands))
                        .setColor(Color.GREEN)
                )).flatMap(m -> m);
    }

    @Override
    public CommandArgs createArgs(McBot mcBot) {
        return null;
    }

    @Override
    public String getName() {
        return "commands";
    }

    @Override
    public boolean hasArgs() {
        return false;
    }
}
