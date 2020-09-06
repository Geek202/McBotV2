package me.geek.tom.mcbot.commands;

import me.geek.tom.mcbot.McBot;
import me.geek.tom.mcbot.commands.api.Command;
import me.geek.tom.mcbot.commands.api.CommandArgs;
import me.geek.tom.mcbot.commands.api.CommandContext;
import me.geek.tom.mcbot.commands.api.ICommand;
import reactor.core.publisher.Mono;

@Command
public class CommandShutdown implements ICommand<CommandArgs> {
    @Override
    public Mono<?> handle(CommandContext<CommandArgs> ctx) {
        if (ctx.getAuthor().map(u -> u.getId().asString().equals(ctx.getMcBot().getOwner())).orElse(false)) {
            return ctx.reply(":wave: Bot shutting down...")
                    .flatMap($ -> ctx.getMcBot().getClient().logout());
        } else {
            return Mono.empty();
        }
    }

    @Override
    public CommandArgs createArgs(McBot mcBot) {
        return null;
    }

    @Override
    public String getName() {
        return "shutdown";
    }

    @Override
    public boolean hasArgs() {
        return false;
    }
}
