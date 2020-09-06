package me.geek.tom.mcbot.commands;

import discord4j.rest.util.Color;
import me.geek.tom.mcbot.McBot;
import me.geek.tom.mcbot.commands.api.Command;
import me.geek.tom.mcbot.commands.api.CommandArgs;
import me.geek.tom.mcbot.commands.api.CommandContext;
import me.geek.tom.mcbot.commands.api.ICommand;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Command
public class CommandPin implements ICommand<CommandArgs> {
    @Override
    public Mono<?> handle(CommandContext<CommandArgs> ctx) {
        int shards = ctx.getMcBot().getClient().getGatewayClientGroup().getShardCount();
        List<String> pings = new ArrayList<>();
        for (int shardId = 0; shardId < shards; shardId++) {
            int finalShardId = shardId;
            ctx.getMcBot().getClient().getGatewayClient(shardId).ifPresent(c ->
                    pings.add("`WS Ping for shard " + finalShardId + ": " + c.getResponseTime().toMillis() + " ms`"));
        }
        return ctx.getChannel().flatMap(channel -> channel.createEmbed(embed -> embed
                .setTitle("Websocket pings across all shards:")
                .setDescription(String.join("\n", pings))
                .setColor(Color.BISMARK)
                .setTimestamp(Instant.now())
        ));
    }

    @Override
    public CommandArgs createArgs(McBot mcBot) {
        return null;
    }

    @Override
    public String getName() {
        return "pin";
    }

    @Override
    public boolean hasArgs() {
        return false;
    }
}
