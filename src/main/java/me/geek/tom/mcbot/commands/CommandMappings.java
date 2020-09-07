package me.geek.tom.mcbot.commands;

import com.beust.jcommander.Parameter;
import discord4j.rest.util.Color;
import me.geek.tom.mcbot.McBot;
import me.geek.tom.mcbot.commands.api.CommandArgs;
import me.geek.tom.mcbot.commands.api.CommandContext;
import me.geek.tom.mcbot.commands.api.ICommand;
import me.geek.tom.mcbot.mappings.*;
import me.geek.tom.mcbot.util.PaginatedMessageSystem;
import me.geek.tom.mcbot.util.Util;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class CommandMappings<P extends MappingsParser, T extends MappingsDatabase<P>> implements ICommand<CommandMappings.Args> {

    private final LookupType lookupType;
    private final String prefix;

    public CommandMappings(LookupType lookupType, String prefix) {
        this.lookupType = lookupType;
        this.prefix = prefix;
    }

    @Override
    public Mono<?> handle(CommandContext<Args> ctx) {
        T database = getDatabase(ctx.getMcBot());
        String searchTerm = ctx.getArgs().args.stream()
                .filter(s -> !s.isEmpty()).findFirst().orElse("");

        if (searchTerm.isEmpty())
            return ctx.showUsage();

        return Mono.just(ctx.getArgs().version)
                .flatMap(database::updateMappings)
                .flatMap(MappingsParser::readMappings)
                .flatMapIterable(l -> l)
                .filter(lookupType::matches)
                .filter(m -> m.matchesSearch(searchTerm))
                .collectList()
                .transform(Util.flatZipWith(ctx.getChannel(), (mappings, channel) -> {
                    if (!mappings.isEmpty()) {
                        List<String> results = mappings.stream().map(Mapping::asDiscordString)
                                .collect(Collectors.toList());
                        if (!ctx.getAuthor().isPresent()) return Mono.empty();
                        PaginatedMessageSystem.Builder builder = PaginatedMessageSystem.INSTANCE.builder(channel)
                                .setPage(0)
                                .setTriggerUser(ctx.getAuthor().get().getId());

                        int maxPerPage = 2;
                        if (results.size() > maxPerPage) {
                            List<List<String>> mps = new ArrayList<>();
                            List<String> current = new ArrayList<>();
                            for (String result : results) {
                                if (current.size() >= maxPerPage) {
                                    mps.add(current);
                                    current = new ArrayList<>();
                                }
                                current.add(result);
                            }
                            if (!current.isEmpty()) mps.add(current);

                            mps.stream().map(p -> new PaginatedMessageSystem.MessagePage(
                                    getTitle(searchTerm),
                                    String.join("\n", p),
                                    Color.BLUE
                            )).forEach(builder::addPage);
                        } else {
                            builder.addPage(new PaginatedMessageSystem.MessagePage(
                                    getTitle(searchTerm),
                                    String.join("\n", results),
                                    Color.BLUE
                            ));
                        }
                        return builder.build().send();
                    } else {
                        return ctx.createError("No results found!");
                    }
                }));
    }

    public abstract T getDatabase(McBot mcBot);
    public abstract CommandMappings<P, T> createSubcommand(LookupType type, String name);
    public abstract String getTitle(String searchTerm);
    public abstract Mono<GameVersion> getLatestVersion(MappingsApiUtils utils);

    @Override
    public Args createArgs(McBot mcBot) {
        Args args = new Args();
        args.version = getLatestVersion(mcBot.getMappingUtils()).map(v -> v.version).block();
        return args;
    }

    @Override
    public boolean hasArgs() {
        return true;
    }

    @Override
    public List<ICommand<?>> subcommands() {
        return Arrays.stream(LookupType.values())
                .filter(t -> t != LookupType.ALL)
                .map(t -> this.createSubcommand(t, prefix + t.getSuffix()))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unused")
    public static class Args extends CommandArgs {
        @Parameter(required = true, description = "<query>")
        public List<String> args;

        @Parameter(names = { "-v", "--version" }, description = "Minecraft version to use")
        public String version;
    }

    public enum LookupType {
        ALL($ -> true, ""),
        METHOD(type -> type == Mapping.Type.METHOD, "m"),
        FIELD(type -> type == Mapping.Type.FIELD, "f"),
        PARAMETER(type -> type == Mapping.Type.FIELD, "p"),
        CLASS(type -> type == Mapping.Type.CLASS, "c");

        private final Predicate<Mapping.Type> matches;
        private final String suffix;

        LookupType(Predicate<Mapping.Type> matches, String suffix) {
            this.matches = matches;
            this.suffix = suffix;
        }

        public boolean matches(Mapping mapping) {
            return matches.test(mapping.getType());
        }

        public String getSuffix() {
            return suffix;
        }
    }

}
