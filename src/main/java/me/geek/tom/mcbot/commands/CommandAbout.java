package me.geek.tom.mcbot.commands;

import discord4j.rest.util.Color;
import me.geek.tom.mcbot.McBot;
import me.geek.tom.mcbot.commands.api.Command;
import me.geek.tom.mcbot.commands.api.CommandArgs;
import me.geek.tom.mcbot.commands.api.CommandContext;
import me.geek.tom.mcbot.commands.api.ICommand;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Command
public class CommandAbout implements ICommand<CommandArgs> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandAbout.class);
    private GitHub github;

    public CommandAbout() {
        try {
            github = GitHub.connectAnonymously();
        } catch (IOException e) {
            LOGGER.error("Failed to connect to GitHub!", e);
            github = null;
        }
    }

    @Override
    public Mono<?> handle(CommandContext<CommandArgs> ctx) {
        String latestUpdate;
        if (github != null) {
            try {
                GHRepository repo = github.getRepository("Geek202/McBotV2");
                PagedIterator<GHCommit> iter = repo.listCommits().iterator();
                if (iter.hasNext()) {
                    GHCommit commit = iter.next();
                    latestUpdate = commit.getAuthor().getName() + ": " + commit.getCommitShortInfo().getMessage();
                } else {
                    latestUpdate = "Failed to get latest commit info!";
                }
            } catch (IOException e) {
                ctx.getMcBot().getSentry().handleUnexpectedError(e, "about command", "Fetching commit info");
                latestUpdate = "Failed to get latest commit info!";
            }
        } else {
            latestUpdate = "Failed to get latest commit info!";
        }

        String finalLatestUpdate = latestUpdate;
        return ctx.reply(embed -> embed
                .setTitle("About McBot")
                .setDescription("McBot is a Discord bot for querying obfuscation mappings for Minecraft.")
                .setColor(Color.LIGHT_SEA_GREEN)
                .addField("Source code", "https://github.com/Geek202/McBotV2", false)
                .addField("Supported mappings", "MCP, Yarn and Mojmap", false)
                .addField("Author", "Created by <@432121968012296192>", false)
                .addField("Invite!", "https://discord.com/api/oauth2/authorize?client_id=719944969992929291&permissions=93248&scope=bot", false)
                .addField("Latest update", finalLatestUpdate, false)
        );
    }

    @Override
    public String getName() {
        return "about";
    }

    @Override
    public CommandArgs createArgs(McBot mcBot) {
        return null;
    }

    @Override
    public boolean hasArgs() {
        return false;
    }
}
