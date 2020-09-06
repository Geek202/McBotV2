package me.geek.tom.mcbot.util;

import reactor.core.publisher.Mono;

import java.util.function.BiFunction;
import java.util.function.Function;

public class Util {

    public static <A, B, C> Function<Mono<A>, Mono<C>> flatZipWith(Mono<? extends B> b, BiFunction<A, B, Mono<C>> combinator) {
        return in -> in.zipWith(b, combinator).flatMap(Function.identity());
    }

}
