package me.geek.tom.mcbot;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.User;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;
import me.geek.tom.mcbot.commands.api.CommandManager;
import me.geek.tom.mcbot.logging.ISentryProxy;
import me.geek.tom.mcbot.logging.NoopSentryProxy;
import me.geek.tom.mcbot.logging.SentryProxy;
import me.geek.tom.mcbot.mappings.MappingsApiUtils;
import me.geek.tom.mcbot.mappings.MappingsDatabase;
import me.geek.tom.mcbot.mappings.MavenDownloader;
import me.geek.tom.mcbot.mappings.mcp.McpDatabase;
import me.geek.tom.mcbot.mappings.mojmap.MojmapDatabase;
import me.geek.tom.mcbot.mappings.yarn.YarnDatabase;
import me.geek.tom.mcbot.util.PaginatedMessageSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class McBot {

    private static final Logger LOGGER = LoggerFactory.getLogger(McBot.class);

    private final GatewayDiscordClient client;
    private final String owner;

    private final MavenDownloader forgeMavenDownloader;
    private final MavenDownloader fabricMavenDownloader;

    private final ISentryProxy sentry;

    private YarnDatabase yarnDatabase;
    private McpDatabase mcpDatabase;
    private MojmapDatabase mojmapDatabase;

    private MappingsApiUtils apiUtils;

    private CommandManager cmdManager;

    public McBot(Args args) {
        client = DiscordClientBuilder.create(args.token)
                .build()
                .login()
                .block();
        owner = args.owner;
        fabricMavenDownloader = new MavenDownloader(new File("mavenStore"), "https://maven.fabricmc.net");
        forgeMavenDownloader = new MavenDownloader(new File("mavenStore"), "https://files.minecraftforge.net/maven/");
        sentry = args.sentryEnabled ? new SentryProxy() : new NoopSentryProxy();
        sentry.init(args.sentryToken);
    }

    private void run() {
        try {
            cmdManager = new CommandManager(this);
            cmdManager.locateCommands(McBot.class.getClassLoader(), "me.geek.tom.mcbot.commands");
            LOGGER.info("Located commands:");
            cmdManager.getCommands().entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue().getClass().getSimpleName())
                    .forEach(LOGGER::info);
        } catch (IOException e) {
            LOGGER.warn("Failed to locate commands: ", e);
        }

        assert client != null;
        client.getEventDispatcher().on(ReadyEvent.class)
                .flatMap(event -> {
                    User self = event.getSelf();
                    LOGGER.info("Logged in to Discord! Gateway version: " + event.getGatewayVersion() + ", Current User: " + self.getTag());
                    sentry.logGuildCount(event.getGuilds().size());
                    Mono<Void> action = client.updatePresence(Presence.idle(Activity.watching("for @" + self.getUsername() + " commands")));
                    if (cmdManager != null) {
                        action = action.then(cmdManager.onBotReady(this));
                    }

                    return action;
                }).subscribe();

        if (cmdManager != null) {
            client.getEventDispatcher().on(MessageCreateEvent.class)
                    .filter(e -> e.getMember().map(user -> !user.isBot()).orElse(false))
                    .filter(e -> {
                        Optional<Snowflake> guildId = e.getGuildId();
                        return e.getMessage().getContent().startsWith(
                                guildId.isPresent() ? cmdManager.getCommandPrefix(guildId.get().asString())
                                        : CommandManager.DEFAULT_COMMAND_PREFIX
                        );
                    })
                    .flatMap(cmdManager::handleCommand)
                    .subscribe();

            client.getEventDispatcher().on(MessageCreateEvent.class)
                    .filter(e -> e.getMember().map(user -> !user.isBot()).orElse(false))
                    .flatMap(e -> e.getClient().getSelf()
                           .flatMap(user -> {
                               if (e.getMessage().getContent().startsWith("<@!"+user.getId().asString()+">" + " commands")) {
                                   return cmdManager.handleCommandCommands(e);
                               } else {
                                   return Mono.empty();
                               }
                           })).subscribe();
        } else {
            LOGGER.warn("Command manager not loaded; this shouldn't have happened! No commands will work!");
        }

        client.getEventDispatcher().on(ReactionAddEvent.class)
                .flatMap(PaginatedMessageSystem.INSTANCE::onReactionAdded)
                .subscribe();

        apiUtils = new MappingsApiUtils();

        yarnDatabase = new YarnDatabase(this);
        mcpDatabase = new McpDatabase(this);
        mojmapDatabase = new MojmapDatabase(this, new File("mojmapData"));

        client.onDisconnect().flatMap(__ -> cmdManager.shutdown()).block();
        MappingsDatabase.shutdown();
        sentry.logBotStop();
        LOGGER.info("So long, and thanks for all the fish!");
    }

    public MavenDownloader getFabricMavenDownloader() {
        return fabricMavenDownloader;
    }

    public MavenDownloader getForgeMavenDownloader() {
        return forgeMavenDownloader;
    }

    public YarnDatabase getYarnDatabase() {
        return yarnDatabase;
    }

    public McpDatabase getMcpDatabase() {
        return mcpDatabase;
    }

    public MojmapDatabase getMojmapDatabase() {
        return mojmapDatabase;
    }

    public MappingsApiUtils getMappingUtils() {
        return apiUtils;
    }

    public GatewayDiscordClient getClient() {
        return client;
    }

    public String getOwner() {
        return owner;
    }

    public ISentryProxy getSentry() {
        return sentry;
    }

    public static void main(String[] args) {
        Args ags = new Args();
        JCommander.newBuilder()
                .addObject(ags)
                .build()
                .parse(args);
        new McBot(ags).run();
    }

    public CommandManager getCommandManager() {
        return cmdManager;
    }

    @SuppressWarnings({"CanBeFinal"})
    private static class Args {
        @Parameter(names = { "-token", "--token", "-t" }, required = true, description = "Bot token to log into Discord with")
        public String token;

        @Parameter(names = { "-owner", "--owner", "-o" }, description = "The Discord user id of the user who runs the bot.")
        public String owner = "432121968012296192"; // default: Me! (Tom_The_Geek#8559 for you stalkers)

        @Parameter(names = { "-sentry", "--sentry", "-s" }, description = "Should the bot send events to sentry.io?")
        public Boolean sentryEnabled = false;

        @Parameter(names = { "-stoken", "--sentry-token", "-st" }, description = "The token to use to send to sentry.io")
        public String sentryToken;
    }
}
