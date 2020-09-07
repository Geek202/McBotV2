package me.geek.tom.mcbot.commands.api;

import com.beust.jcommander.JCommander;
import com.google.common.reflect.ClassPath;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Color;
import me.geek.tom.mcbot.McBot;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CommandManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);

    public static final String COMMAND_PREFIX = "m!";

    private final Map<String, ICommand<?>> commands;
    private final McBot mcBot;

    public CommandManager(McBot mcBot) {
        this.mcBot = mcBot;
        this.commands = new ConcurrentHashMap<>();
    }

    @SuppressWarnings("UnstableApiUsage")
    public void locateCommands(ClassLoader loader, String pkg) throws IOException, IllegalAccessException, InstantiationException {
        ClassPath classPath = ClassPath.from(loader);
        for (ClassPath.ClassInfo info : classPath.getTopLevelClasses(pkg)) {
            Class<?> cls = info.load();
            if (cls.isAnnotationPresent(Command.class)) {
                if (!ICommand.class.isAssignableFrom(cls)) {
                    LOGGER.warn("Found @Command annotated class " + cls.getSimpleName() + " that does not implement ICommand, ignoring...");
                    continue;
                }
                ICommand<?> cmd = (ICommand<?>) cls.newInstance();
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

                this.commands.put(name, cmd);
            }
        }
    }

    public <T extends CommandArgs> Mono<?> handleCommand(MessageCreateEvent event) {
        String content = event.getMessage().getContent();
        String command = content.substring(COMMAND_PREFIX.length());
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

    public Map<String, ICommand<?>> getCommands() {
        return commands;
    }
}
