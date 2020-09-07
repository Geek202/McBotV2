package me.geek.tom.mcbot.commands;

import com.beust.jcommander.Parameter;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Member;
import discord4j.rest.util.Permission;
import me.geek.tom.mcbot.McBot;
import me.geek.tom.mcbot.commands.api.Command;
import me.geek.tom.mcbot.commands.api.CommandArgs;
import me.geek.tom.mcbot.commands.api.CommandContext;
import me.geek.tom.mcbot.commands.api.ICommand;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Command
public class CommandAdmin implements ICommand<CommandAdmin.AdminArgs> {

    @Override
    public Mono<?> handle(CommandContext<AdminArgs> ctx) {
        String newValue = ctx.getArgs().args.stream()
                .filter(s -> !s.isEmpty()).findFirst().orElse("");

        if (newValue.isEmpty())
            return ctx.showUsage();
        Optional<Snowflake> guildId = ctx.getGuildId();
        return guildId.map(snowflake -> ctx.getMember()
                .flatMap(Member::getBasePermissions)
                .flatMap(permissions -> {
                    if (!permissions.contains(Permission.MANAGE_GUILD))
                        return ctx.createError("You do not have permission to do that!");

                    switch (ctx.getArgs().setting) {
                        case PREFIX:
                            ctx.getManager().updateCommandPrefix(snowflake.asString(), newValue);
                            break;
                    }

                    return ctx.reply("Updated " + ctx.getArgs().setting + " to " + newValue + "!");
                })).orElseGet(() -> ctx.createError("Must be run in a server!"));
    }

    @Override
    public AdminArgs createArgs(McBot mcBot) {
        return new AdminArgs();
    }

    @Override
    public String getName() {
        return "admin";
    }

    @Override
    public boolean hasArgs() {
        return true;
    }

    @SuppressWarnings("unused")
    public static class AdminArgs extends CommandArgs {
        @Parameter(description = "<value>", required = true)
        public List<String> args;

        @Parameter(names = { "-setting", "--setting", "-s" }, required = true, description = "What setting to change")
        public Setting setting;
    }

    public enum Setting {
        PREFIX
    }

}
