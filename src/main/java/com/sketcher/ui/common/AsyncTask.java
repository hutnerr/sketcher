package com.sketcher.ui.common;

import javafx.application.Platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Central place for off-FX-thread work (PLAN.md 3, threading rule): one
 * single-threaded executor for DB access (SQLite connections aren't safe to
 * share across threads) and a small pool for file I/O / hashing / decoding.
 * Results are always marshalled back to the FX thread via Platform.runLater.
 */
public final class AsyncTask {
    private static final ExecutorService DB_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sketcher-db");
        t.setDaemon(true);
        return t;
    });

    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "sketcher-io");
        t.setDaemon(true);
        return t;
    });

    private AsyncTask() {
    }

    public static <T> void runDb(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        run(DB_EXECUTOR, work, onSuccess, onError);
    }

    public static void runDb(Runnable work, Runnable onSuccess, Consumer<Throwable> onError) {
        runDb(() -> {
            work.run();
            return null;
        }, ignored -> onSuccess.run(), onError);
    }

    public static <T> void runIo(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        run(IO_EXECUTOR, work, onSuccess, onError);
    }

    private static <T> void run(ExecutorService executor, Supplier<T> work, Consumer<T> onSuccess,
                                 Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                T result = work.get();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable t) {
                Platform.runLater(() -> onError.accept(t));
            }
        });
    }

    public static void shutdown() {
        DB_EXECUTOR.shutdownNow();
        IO_EXECUTOR.shutdownNow();
    }
}
