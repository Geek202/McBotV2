package me.geek.tom.mcbot.commands.api;

import com.beust.jcommander.JCommander;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.Color;
import me.geek.tom.mcbot.McBot;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "FieldCanBeLocal", "unused"})
public class CommandContext<T extends CommandArgs> {

    private final McBot mcBot;
    private final JCommander jCommander;
    private final CommandManager manager;

    private final String commandName;
    @Nullable
    private final Message message;
    private final Optional<Snowflake> guildId;

    private final T args;

    private final Mono<Guild> guild;
    private final Mono<MessageChannel> channel;
    private final Optional<User> author;
    private final Mono<Member> member;

    public CommandContext(McBot mcBot, @Nullable JCommander jCommander, String commandName, MessageCreateEvent event, T args) {
        this(mcBot, jCommander, commandName, event.getMessage(), event.getGuildId(), args);
    }

    public CommandContext(McBot mcBot, @Nullable JCommander jCommander, String commandName, Message message, Optional<Snowflake> guildId, T args) {
        this.manager = mcBot.getCommandManager();
        this.mcBot = mcBot;
        this.jCommander = jCommander;
        this.commandName = commandName;
        this.message = message;
        this.guildId = guildId;

        this.args = args;

        this.guild = message.getGuild().cache();
        this.channel = message.getChannel().cache();
        this.author = message.getAuthor();
        this.member = message.getAuthorAsMember().cache();
    }

    public Mono<Message> reply(String message) {
        return channel.flatMap(channel -> channel.createMessage(message));
    }

    public Mono<Message> showUsage() {
        if (jCommander == null) // Why is a command with no arguments asking for usage \__(O.O)__/
            return Mono.empty();
        StringBuilder usage = new StringBuilder();
        jCommander.setProgramName("m!" + commandName);
        jCommander.usage(usage);
        return channel.flatMap(channel -> channel.createEmbed(embed -> embed
                .setTitle("Usage:")
                .setDescription(Arrays.stream(usage.toString().split("[\n\r]"))
                        .map(s -> "`" + s + "`").collect(Collectors.joining("\n")))
                .setColor(Color.ORANGE)
        ));
    }

    public Mono<Message> createError(String message) {
        return channel.flatMap(channel -> channel.createEmbed(embed -> embed
                .setTitle("Error!")
                .setDescription("`" + message + "`")
                .setColor(Color.RED)));
    }

    public Mono<MessageChannel> getChannel() {
        return this.channel;
    }

    public McBot getMcBot() {
        return mcBot;
    }

    public T getArgs() {
        return args;
    }

    public Optional<User> getAuthor() {
        return author;
    }

    public CommandManager getManager() {
        return manager;
    }

    public Optional<Snowflake> getGuildId() {
        return guildId;
    }

    public Mono<Member> getMember() {
        return member;
    }
}
