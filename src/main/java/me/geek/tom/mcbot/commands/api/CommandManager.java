package me.geek.tom.mcbot.commands.api;

import com.beust.jcommander.JCommander;
import com.google.common.reflect.ClassPath;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Color;
import me.geek.tom.mcbot.McBot;
import me.geek.tom.mcbot.commands.CommandCommands;
import me.geek.tom.mcbot.storage.GeneralGuildSettings;
import me.geek.tom.mcbot.storage.api.GuildStorageManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class CommandManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);

    public static final String DEFAULT_COMMAND_PREFIX = "m!";

    private final Map<String, ICommand<?>> commands;
    private final McBot mcBot;

    private final GuildStorageManager<GeneralGuildSettings> settings;

    private CommandCommands commandCommands;

    public CommandManager(McBot mcBot) {
        this.mcBot = mcBot;
        this.commands = new ConcurrentHashMap<>();
        settings = new GuildStorageManager<>(new File("configs", "general"), GeneralGuildSettings::new);
    }

    @SuppressWarnings("UnstableApiUsage")
    public void locateCommands(ClassLoader loader, String pkg) throws IOException {
        ClassPath classPath = ClassPath.from(loader);
        for (ClassPath.ClassInfo info : classPath.getTopLevelClasses(pkg)) {
            Class<?> cls = info.load();
            if (cls.isAnnotationPresent(Command.class)) {
                if (!ICommand.class.isAssignableFrom(cls)) {
                    LOGGER.warn("Found @Command annotated class " + cls.getSimpleName() + " that does not implement ICommand, ignoring...");
                    continue;
                }
                Supplier<? extends ICommand<?>> constructor = () -> {
                    try {
                        return (ICommand<?>) cls.getDeclaredConstructor().newInstance();
                    } catch (NoSuchMethodException e) {
                        try {
                            return (ICommand<?>) cls.getDeclaredConstructor(McBot.class).newInstance(mcBot);
                        } catch (NoSuchMethodException e1) {
                            LOGGER.warn("No valid command constructor found for command: " + cls.getSimpleName());
                            return null;
                        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e1) {
                            throw new RuntimeException(cls.getSimpleName(), e);
                        }
                    } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
                        throw new RuntimeException(cls.getSimpleName(), e);
                    }
                };
                ICommand<?> cmd = constructor.get();
                if (cmd == null) {
                    LOGGER.warn("Failed to construct command class: " + cls.getSimpleName());
                    continue;
                }

                String name = cmd.getName();
                if (this.commands.containsKey(name)) {
                    LOGGER.warn("Found command " + cls.getSimpleName() + " with name: " + name +
                            " which is the same as " + this.commands.get(name).getClass().getSimpleName() + "! Ignoring...");
                    continue;
                }

                for (ICommand<?> subcommand : cmd.subcommands()) {
                    if (this.commands.containsKey(subcommand.getName())) {
                        LOGGER.warn("Found command " + cls.getSimpleName() + " with name: " + name +
                                " which is the same as " + this.commands.get(name).getClass().getSimpleName() + "! Ignoring...");
                        continue;
                    }
                    this.commands.put(subcommand.getName(), subcommand);
                }

                if (cmd instanceof CommandCommands)
                    this.commandCommands = (CommandCommands) cmd;

                this.commands.put(name, cmd);
            }
        }

        settings.start();
    }

    public Mono<?> handleCommandCommands(MessageCreateEvent event) {
        if (commandCommands != null)
            return commandCommands.handle(new CommandContext<>(mcBot, null, "commands", event, null))
                    .onErrorResume(ClientException.class, e -> {
                        if (e != null && e.getStatus().code() == 403) {
                            return Mono.empty();
                        } else if (e != null) {
                            return Mono.error(e);
                        } else {
                            return Mono.empty();
                        }
                    });
        else
            return handleException(event, new RuntimeException("Failed to locate commands command!"), "running command 'commands'");
    }

    public Mono<Void> onBotReady(McBot mcBot) {
        return Mono.just(commands.values())
                .flatMapIterable(c -> c)
                .map(c -> {
                    c.onBotStarting(mcBot);
                    return true;
                }).collectList()
                .then(Mono.empty());

    }

    public Mono<Void> onBotShutdown(McBot mcBot) {
        return Mono.just(commands.values())
                .flatMapIterable(c -> c)
                .map(c -> {
                    c.onBotStopping(mcBot);
                    return true;
                }).collectList()
                .then(Mono.empty());

    }

    public <T extends CommandArgs> Mono<?> handleCommand(MessageCreateEvent event) {
        String content = event.getMessage().getContent();
        Optional<Snowflake> guildId = event.getGuildId();
        String command = content.substring((guildId.isPresent() ? getCommandPrefix(guildId.get().asString())
                : DEFAULT_COMMAND_PREFIX).length());
        String[] commandName = command.split(" ", 2);
        if (commandName.length < 1) {
            return Mono.empty();
        }

        try {
            @SuppressWarnings("unchecked")
            ICommand<T> cmd = (ICommand<T>) this.commands.get(commandName[0]);
            if (cmd == null)
                return event.getMessage().getChannel().map(channel -> channel.createEmbed(embed -> embed
                        .setTitle("McBot error!")
                        .setDescription("`Command not found!`")));
            try {
                T t;
                String message = event.getMessage().getContent();
                JCommander jCommander = null;
                if (cmd.hasArgs()) {
                    t = cmd.createArgs(mcBot);
                    int firstSpace = message.indexOf(" ");
                    jCommander = JCommander.newBuilder()
                            .addObject(t)
                            .build();
                    jCommander.parse((firstSpace >= 0 ? message.substring(firstSpace) : "").split(" "));
                } else {
                    t = null;
                }
                JCommander finalJCommander = jCommander;
                return event.getMessage().getChannel()
                        .flatMap(channel -> channel
                                .typeUntil(cmd.handle(new CommandContext<>(mcBot, finalJCommander, commandName[0], event, t))
                                .doOnError(e -> LOGGER.error("Error processing command: ", e))
                                .onErrorResume(e -> handleException(event, e, "Command handler: " + cmd.getClass().getSimpleName())
                                        .then(Mono.empty()))
                                ).then())
                        .onErrorResume(ClientException.class, e -> {
                            if (e != null && e.getStatus().code() == 403) {
                                return Mono.empty();
                            } else if (e != null) {
                                return Mono.error(e);
                            } else {
                                return Mono.empty();
                            }
                        });
            } catch (Exception e) {
                return handleException(event, e, "Handling command input");
            }
        } catch (ClassCastException e) {
            return event.getMessage().getChannel().flatMap(channel -> channel.createEmbed(embed -> embed
                    .setTitle("McBot error!")
                    .setDescription("`" + e.getClass().getSimpleName() + ": " +
                            String.join("`\n`", e.getMessage().split("[\n\r]")))));
        }
    }

    public void updateCommandPrefix(String guildId, String newPrefix) {
        settings.modify(guildId, settings ->
                settings.setCommandPrefix(newPrefix));
    }

    public String getCommandPrefix(String guildId) {
        return settings.read(guildId).getCommandPrefix();
    }

    @NotNull
    private Mono<Message> handleException(MessageCreateEvent event, Throwable t, String context) {
        return event.getMessage().getChannel().flatMap(channel -> channel.createEmbed(embed -> embed
                        .setTitle("McBot error!")
                        .setDescription("`" + t.getClass().getSimpleName() + ": " +
                                String.join("`\n`", t.getMessage().split("[\n\r]")) + "`")
                        .setColor(Color.RED)
                ).doOnNext($ -> mcBot.getSentry().handleUnexpectedError(t, event.getMessage().getContent(), context))
        );
    }

    public Mono<Void> shutdown() {
        return Mono.fromRunnable(settings::stop)
                .then(onBotShutdown(mcBot));
    }

    public Map<String, ICommand<?>> getCommands() {
        return commands;
    }
}
