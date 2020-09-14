package me.geek.tom.mcbot.commands;

import com.beust.jcommander.Parameter;
import com.github.steveice10.opennbt.tag.builtin.*;
import com.google.common.collect.Streams;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import me.geek.tom.mcbot.McBot;
import me.geek.tom.mcbot.commands.api.Command;
import me.geek.tom.mcbot.commands.api.CommandArgs;
import me.geek.tom.mcbot.commands.api.CommandContext;
import me.geek.tom.mcbot.commands.api.ICommand;
import me.geek.tom.mcbot.storage.api.GuildDataStorage;
import me.geek.tom.mcbot.storage.api.GuildStorageManager;
import me.geek.tom.mcbot.util.PaginatedMessageSystem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Command
public class CommandQuote implements ICommand<CommandQuote.QuoteArgs> {

    private final GuildStorageManager<QuoteStorage> storage;

    public CommandQuote() {
        storage = new GuildStorageManager<>(new File("quote_storage"), QuoteStorage::new);
    }

    @Override
    public Mono<?> handle(CommandContext<QuoteArgs> ctx) {
        if (!ctx.getGuildId().isPresent())
            return ctx.createError("Can only be ran in a server!");

        QuoteArgs args = ctx.getArgs();
        String quote = args.args.stream()
                .filter(s -> !s.isEmpty()).collect(Collectors.joining(" "));

        Snowflake guildId = ctx.getGuildId().get();

        Optional<User> optionalAuthor = ctx.getAuthor();
        if (!optionalAuthor.isPresent()) // WTF WHERE IS THE AUTHOR?!
            return Mono.empty(); // Something is very wrong. Lets just hope nothing is broken :{

        if (quote.isEmpty()) {
            return Mono.just(storage.read(guildId.asString()).quotes)
                    .zipWith(ctx.getChannel())
                    .flatMap(tp -> {
                        List<String> quotes = tp.getT1().stream()
                                .map(q -> "`#" + q.id + " - " + q.person + "`").collect(Collectors.toList());
                        MessageChannel channel = tp.getT2();
                        PaginatedMessageSystem.Builder builder = PaginatedMessageSystem.INSTANCE.builder(channel)
                                .setPage(0)
                                .setTriggerUser(optionalAuthor.get().getId());

                        int maxPerPage = 15;
                        if (quotes.size() > maxPerPage) {
                            List<List<String>> mps = new ArrayList<>();
                            List<String> current = new ArrayList<>();
                            for (String result : quotes) {
                                if (current.size() >= maxPerPage) {
                                    mps.add(current);
                                    current = new ArrayList<>();
                                }
                                current.add(result);
                            }
                            if (!current.isEmpty()) mps.add(current);

                            mps.stream().map(p -> new PaginatedMessageSystem.MessagePage(
                                    "List of quotes:",
                                    String.join("\n", p),
                                    Color.DEEP_LILAC
                            )).forEach(builder::addPage);
                        } else {
                            builder.addPage(new PaginatedMessageSystem.MessagePage(
                                    "List of quotes:",
                                    String.join("\n", quotes),
                                    Color.DEEP_LILAC
                            ));
                        }

                        return builder.build().send();
                    });
        }

        return ctx.getChannel()
                .flatMap(channel -> {
                    if (args.add && args.remove)
                        return ctx.createError("Cannot add and remove a quote at the same time!");

                    if (args.add) {
                        User author = optionalAuthor.get();
                        AtomicInteger id = new AtomicInteger();

                        storage.modify(guildId.asString(), storage -> {
                            String person = args.author.isEmpty() ? author.getTag() : args.author;
                            Quote q = new Quote(quote, person, author.getId().asLong(), storage.nextId++);
                            storage.quotes.add(q);
                            id.set(q.id);
                        });

                        return ctx.reply("Added quote `#" + id.get() + "`");
                    } else if (args.remove) {
                        try {
                            int quoteId = Integer.parseInt(quote);
                            Optional<Quote> foundQuote = storage.read(guildId.asString()).quotes.stream()
                                    .filter(q -> q.id == quoteId).findFirst();
                            if (!foundQuote.isPresent())
                                return ctx.createError("Quote not found!");
                            Quote q = foundQuote.get();

                            return ctx.getMember()
                                    .flatMap(Member::getBasePermissions)
                                    .flatMap(permissions -> {
                                        if (q.owner != optionalAuthor.get().getId().asLong() && !permissions.contains(Permission.MANAGE_MESSAGES)) {
                                            return ctx.createError("Can only remove your own quote, " +
                                                    "unless you have the MANAGE_MESSAGES permission!");
                                        }
                                        storage.modify(guildId.asString(), storage ->
                                                storage.quotes.remove(q));
                                        return ctx.reply("Successfully removed quote `#" + quoteId + "`!");
                                    });
                        } catch (NumberFormatException e) {
                            return ctx.createError(quote + " is not a number!");
                        }
                    } else {
                        try {
                            int quoteId = Integer.parseInt(quote);
                            Optional<Quote> foundQuote = storage.read(guildId.asString()).quotes.stream()
                                    .filter(q -> q.id == quoteId).findFirst();
                            if (!foundQuote.isPresent())
                                return ctx.createError("Quote not found!");
                            Quote q = foundQuote.get();

                            return ctx.reply(embed -> embed
                                    .setTitle("Quote #" + quoteId)
                                    .setDescription(">>> " + q.content)
                                    .setFooter(q.person, null)
                                    .setColor(Color.DEEP_LILAC)
                            );
                        } catch (NumberFormatException e) {
                            return ctx.createError(quote + " is not a number!");
                        }
                    }
                });
    }

    @Override
    public String getName() {
        return "quote";
    }

    @Override
    public QuoteArgs createArgs(McBot mcBot) {
        return new QuoteArgs();
    }

    @Override
    public boolean hasArgs() {
        return true;
    }

    @Override
    public void onBotStarting(McBot mcBot) {
        this.storage.start();
    }

    @Override
    public void onBotStopping(McBot mcBot) {
        this.storage.stop();
    }

    public static class QuoteStorage extends GuildDataStorage {
        private List<Quote> quotes;
        private int nextId;

        public QuoteStorage() {
            this.quotes = new ArrayList<>();
            this.nextId = 1;
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            if (tag.contains("Quotes")) {
                ListTag quotesTag = tag.get("Quotes");
                quotes = Streams.stream(quotesTag).map(quoteTag -> new Quote().read((CompoundTag) quoteTag)).collect(Collectors.toList());
            }
            if (tag.contains("NextQuoteId"))
                nextId = (int) tag.get("NextQuoteId").getValue();
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            ListTag quotesTag = new ListTag("Quotes");
            quotes.stream().map(Quote::write).forEach(quotesTag::add);
            tag.put(quotesTag);
            tag.put(new IntTag("NextQuoteId", nextId));
        }
    }

    public static class Quote {
        private String content;
        private String person;
        private long owner;
        private int id;

        public Quote() { }
        public Quote(String content, String person, long owner, int id) {
            this.content = content;
            this.person = person;
            this.owner = owner;
            this.id = id;
        }

        public Quote read(CompoundTag tag) {
            if (tag.contains("Content"))
                content = (String) tag.get("Content").getValue();
            if (tag.contains("Person"))
                person = (String) tag.get("Person").getValue();
            if (tag.contains("Owner"))
                owner = (long) tag.get("Owner").getValue();
            if (tag.contains("Id"))
                id = (int) tag.get("Id").getValue();
            return this;
        }

        public CompoundTag write() {
            CompoundTag tag = new CompoundTag("");
            tag.put(new StringTag("Content", content));
            tag.put(new StringTag("Person", person));
            tag.put(new LongTag("Owner", owner));
            tag.put(new IntTag("Id", id));
            return tag;
        }
    }

    public static class QuoteArgs extends CommandArgs {
        @Parameter(description = "<input>")
        public List<String> args = new ArrayList<>();

        @Parameter(description = "Add a new quote", names = { "-a", "--add" })
        public boolean add = false;
        @Parameter(description = "Remove a quote you own", names = { "-r", "-d", "--remove", "--delete" })
        public boolean remove = false;

        @Parameter(description = "User who said the quote (use with --add)", names = { "-u", "--user" })
        public String author = "";
    }
}
