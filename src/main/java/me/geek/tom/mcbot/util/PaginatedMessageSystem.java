package me.geek.tom.mcbot.util;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.rest.util.Color;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PaginatedMessageSystem {

    private static final String LEFT_ARROW = "\u2B05";
    private static final String RIGHT_ARROW = "\u27A1";

    public static final PaginatedMessageSystem INSTANCE = new PaginatedMessageSystem();

    private final Long2ObjectMap<PaginatedMessage> messages = new Long2ObjectOpenHashMap<>();
    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();

    private PaginatedMessageSystem() { }

    public static class PaginatedMessage {
        private final List<MessagePage> pages;
        private final MessageChannel channel;
        private final Snowflake triggerUser;

        private int currentPage;
        private Mono<Message> message;

        private PaginatedMessage(List<MessagePage> pages, int page, MessageChannel channel, Snowflake triggerUser) {
            this.pages = pages;
            this.channel = channel;
            this.triggerUser = triggerUser;
            this.currentPage = page;
        }

        public Mono<Message> send() {
            return message = pages.get(currentPage).send(channel, currentPage + 1, pages.size())
                    .doOnNext(message -> INSTANCE.messages.put(message.getId().asLong(), this))
                    .flatMap(msg -> msg.addReaction(ReactionEmoji.unicode(LEFT_ARROW)).thenReturn(msg))
                    .flatMap(msg -> msg.addReaction(ReactionEmoji.unicode(RIGHT_ARROW)).thenReturn(msg))
                    .doOnNext(msg -> {
                        long msgId = msg.getId().asLong();
                        if (!INSTANCE.cleanup.isShutdown()) {
                            INSTANCE.cleanup.schedule(() -> {
                                if (INSTANCE.messages.containsKey(msgId))
                                    INSTANCE.messages.remove(msgId);
                            }, 60, TimeUnit.SECONDS);
                        }
                    }).cache();
        }

        private Mono<Message> advancePage() {
            return currentPage + 1 < pages.size() ? setCurrentPage(currentPage + 1) : Mono.empty();
        }

        private Mono<Message> previousPage() {
            return currentPage - 1 >= 0 ? setCurrentPage(currentPage - 1) : Mono.empty();
        }

        private Mono<Message> setCurrentPage(int page) {
            if (page < 0 || page >= pages.size())
                throw new IllegalArgumentException("Invalid page number!");
            this.currentPage = page;
            return message != null ? message.flatMap(msg -> this.pages.get(page).modify(msg, page + 1, pages.size())) : Mono.empty();
        }
    }

    public static class MessagePage {
        private final String title;
        private final String description;
        private final Color colour;

        public MessagePage(String title, String description, Color colour) {
            this.title = title;
            this.description = description;
            this.colour = colour;
        }

        private Mono<Message> send(MessageChannel channel, int pageNum, int maxPages) {
            return channel.createEmbed(embed -> embed
                    .setTitle(title)
                    .setDescription(description)
                    .setFooter("Page " + pageNum + "/" + maxPages, null)
                    .setColor(colour)
            );
        }

        private Mono<Message> modify(Message message, int pageNum, int maxPages) {
            return message.edit(msg -> msg
                    .setEmbed(embed -> embed
                            .setTitle(title)
                            .setDescription(description)
                            .setFooter("Page " + pageNum + "/" + maxPages, null)
                            .setColor(colour)
            ));
        }
    }

    public static class Builder {
        private final List<MessagePage> pages = new ArrayList<>();
        private final MessageChannel channel;
        private int page;
        private Snowflake triggerUser;
        
        private Builder(MessageChannel channel) {
            this.channel = channel;
        }
        
        @SuppressWarnings("UnusedReturnValue")
        public Builder addPage(MessagePage page) {
            pages.add(page);
            return this;
        }
        
        public Builder setPage(int page) {
            this.page = page;
            return this;
        }
        
        public Builder setTriggerUser(Snowflake user) {
            this.triggerUser = user;
            return this;
        }
        
        public PaginatedMessage build() {
            return new PaginatedMessage(pages, page, channel, triggerUser);
        }
    }

    public Builder builder(MessageChannel channel) {
        return new Builder(channel);
    }

    public Mono<Void> shutdown() {
        return Mono.fromRunnable(this.cleanup::shutdown);
    }

    public Mono<?> onReactionAdded(ReactionAddEvent event) {
        Snowflake messageId = event.getMessageId();
        ReactionEmoji emoji = event.getEmoji();
        if (event.getClient().getSelfId().equals(event.getUserId())) return Mono.empty();
        Optional<ReactionEmoji.Unicode> unicode = emoji.asUnicodeEmoji();
        if (!unicode.isPresent()) return event.getMessage().flatMap(msg -> msg.removeReaction(emoji, event.getUserId()));
        PaginatedMessage message = messages.get(messageId.asLong());
        if (message != null) {
            if (!event.getUserId().equals(message.triggerUser)) return Mono.empty();
            Mono<?> ret = Mono.empty();
            switch (unicode.get().getRaw()) {
                case LEFT_ARROW:
                    ret = ret.then(message.previousPage());
                    break;
                case RIGHT_ARROW:
                    ret = ret.then(message.advancePage());
                    break;
                default:
                    break;
            }
            return event.getMessage()
                    .flatMap(msg -> msg.removeReaction(emoji, event.getUserId()))
                    .then(ret);
        } else {
            return Mono.empty();
        }
    }
}
