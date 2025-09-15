package com.spawneditor.rpc;

import com.spawneditor.model.Tile;
import de.jcm.discordgamesdk.activity.Activity;
import de.jcm.discordgamesdk.ActivityManager;
import de.jcm.discordgamesdk.Core;
import de.jcm.discordgamesdk.CreateParams;
import de.jcm.discordgamesdk.LogLevel;
import de.jcm.discordgamesdk.Result;
import de.jcm.discordgamesdk.activity.ActivityAssets;
import de.jcm.discordgamesdk.activity.ActivityTimestamps;
import de.jcm.discordgamesdk.activity.ActivityType;

import java.io.Closeable;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public final class DiscordPresence implements Closeable {
    private static final long CLIENT_ID = 1409387346511794220L;
    private final Core core;
    private final Thread loopThread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean loopRunningRef;
    private final Instant startInstant = Instant.now();
    private static PrintStream ORIGINAL_OUT = System.out;
    private static PrintStream ORIGINAL_ERR = System.err;
    private static final Predicate<String> NOISY_LINE = s -> {
        if (s == null) return false;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return true;
        if (trimmed.startsWith("[]")) return true;
        String u = trimmed.toUpperCase();
        if (u.contains("VERBOSE")) return true;
        if (u.contains("SUBSCRIBE")) return true;
        if (u.contains("GET_RELATIONSHIPS")) return true;
        if (u.contains("SENT STRING")) return true;
        if (u.contains("RECEIVED STRING")) return true;
        return false;
    };
    private volatile String details = "No Tile Selected";
    private volatile String state = "Active";

    public static DiscordPresence start() {
        installFilteredStreams();
        try {
            CreateParams params = new CreateParams();
            params.setClientID(CLIENT_ID);
            params.setFlags(CreateParams.getDefaultFlags());
            Core core = new Core(params);
            core.setLogHook(LogLevel.ERROR, (lvl, msg) -> {
                if (!shouldLog(lvl, msg)) return;
                System.err.println("[Discord] " + lvl + ": " + msg);
            });
            AtomicBoolean loopRunning = new AtomicBoolean(true);
            Thread thread = new Thread(() -> {
                try {
                    while (loopRunning.get() && core.isOpen()) {
                        core.runCallbacks();
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    }
                } catch (Exception e) {
                    System.err.println("[Discord] callback loop stopped: " + e.getMessage());
                }
            }, "discord-rpc-callbacks");
            thread.setDaemon(true);
            thread.start();
            DiscordPresence dp = new DiscordPresence(core, thread, loopRunning);
            dp.push();
            return dp;
        } catch (Throwable t) {
            System.err.println("[Discord] init failed: " + t.getMessage());
            return new DiscordPresence(null, null, new AtomicBoolean(false));
        }
    }

    private static boolean shouldLog(LogLevel lvl, String msg) {
        if (lvl == null || msg == null) return false;
        if (NOISY_LINE.test(msg)) return false;
        return lvl == LogLevel.ERROR;
    }

    private DiscordPresence(Core core, Thread loopThread, AtomicBoolean loopRunningFlag) {
        this.core = core;
        this.loopThread = loopThread;
        this.loopRunningRef = loopRunningFlag;
    }

    public void setTile(Tile t) {
        if (core == null || !running.get() || t == null) return;
        this.details = "Editing Tile: " + t.x + ", " + t.y + ", " + t.z;
        push();
    }

    public void setActive(boolean active) {
        if (core == null || !running.get()) return;
        this.state = active ? "Active" : "Inactive";
        push();
    }

    private void push() {
        if (core == null) return;
        ActivityManager am = core.activityManager();
        Activity act = new Activity();
        act.setType(ActivityType.PLAYING);
        act.setDetails(details);
        act.setState(state);
        ActivityTimestamps ts = act.timestamps();
        ts.setStart(startInstant);
        ActivityAssets assets = act.assets();
        assets.setLargeImage("xselarge");
        assets.setLargeText("Spawn Editor by Xeon");
        assets.setSmallImage("xsetooltip");
        assets.setSmallText("x3on.xyz/rsps");
        am.updateActivity(act, (Result r) -> {
            if (r != Result.OK) System.err.println("[Discord] updateActivity failed: " + r);
        });
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) return;
        if (loopRunningRef != null) loopRunningRef.set(false);
        if (core != null) {
            try {
                core.activityManager().clearActivity(Core.DEFAULT_CALLBACK);
            } catch (Throwable ignored) {
            }
            try {
                core.close();
            } catch (Throwable ignored) {
            }
        }
        if (loopThread != null) {
            try {
                loopThread.interrupt();
            } catch (Throwable ignored) {
            }
        }
        restoreOriginalStreams();
    }

    private static void installFilteredStreams() {
        if (!(System.out instanceof LineFilteringPrintStream)) {
            System.setOut(new LineFilteringPrintStream(ORIGINAL_OUT, NOISY_LINE));
        }
        if (!(System.err instanceof LineFilteringPrintStream)) {
            System.setErr(new LineFilteringPrintStream(ORIGINAL_ERR, NOISY_LINE));
        }
    }

    private static void restoreOriginalStreams() {
        if (System.out instanceof LineFilteringPrintStream) {
            System.setOut(ORIGINAL_OUT);
        }
        if (System.err instanceof LineFilteringPrintStream) {
            System.setErr(ORIGINAL_ERR);
        }
    }

    private static final class LineFilteringPrintStream extends PrintStream {
        private final PrintStream delegate;
        private final Predicate<String> drop;
        private final StringBuilder buf = new StringBuilder(256);

        LineFilteringPrintStream(PrintStream delegate, Predicate<String> drop) {
            super(new NoopOutputStream());
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.drop = Objects.requireNonNull(drop, "drop");
        }

        @Override
        public void println(String x) {
            writeLine(x == null ? "null" : x);
        }

        @Override
        public void println(Object x) {
            writeLine(String.valueOf(x));
        }

        @Override
        public void print(String s) {
            writeChunk(s == null ? "null" : s);
        }

        @Override
        public void print(Object obj) {
            writeChunk(String.valueOf(obj));
        }

        @Override public void write(byte[] b, int off, int len) {
            String s;
            try {
                s = new String(b, off, len, StandardCharsets.UTF_8);
            } catch (Exception e) {
                delegate.write(b, off, len);
                return;
            }
            writeChunk(s);
        }

        private void writeChunk(String s) {
            int start = 0;
            int idx;
            while ((idx = s.indexOf('\n', start)) >= 0) {
                buf.append(s, start, idx);
                writeLine(buf.toString());
                buf.setLength(0);
                start = idx + 1;
            }
            if (start < s.length()) {
                buf.append(s, start, s.length());
            }
        }

        private void writeLine(String line) {
            if (!drop.test(line)) {
                delegate.println(line);
            }
        }

        @Override
        public void flush() {
            if (buf.length() > 0) {
                String line = buf.toString();
                buf.setLength(0);
                if (!drop.test(line)) {
                    delegate.print(line);
                }
            }
            delegate.flush();
        }

        @Override public void close() {
            flush();
        }
    }

    private static final class NoopOutputStream extends OutputStream {
        @Override
        public void write(int b) {
        }
    }
}
