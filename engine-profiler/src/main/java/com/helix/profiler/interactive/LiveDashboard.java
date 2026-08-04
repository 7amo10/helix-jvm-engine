package com.helix.profiler.interactive;

import com.helix.profiler.gc.GcLogAnalyzer;
import com.helix.profiler.gc.GcStatistics;
import com.helix.profiler.jit.CompilationStats;
import com.helix.profiler.jit.JitCompilationMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Interactive Live Dashboard manager that refreshes and renders JVM stats to the terminal every 1 second.
 */
public class LiveDashboard {

    private static final Logger log = LoggerFactory.getLogger(LiveDashboard.class);

    private final DashboardRenderer renderer;
    private final JitCompilationMonitor jitMonitor;
    private final GcLogAnalyzer gcAnalyzer;
    private final PrintStream outputStream;
    private final ScheduledExecutorService scheduler;

    private volatile boolean running;
    private long activeClassLoaders;
    private long cachedRulesCount;

    public LiveDashboard() {
        this(null, null, System.out);
    }

    public LiveDashboard(JitCompilationMonitor jitMonitor, GcLogAnalyzer gcAnalyzer, PrintStream outputStream) {
        this.renderer = new DashboardRenderer();
        this.jitMonitor = jitMonitor;
        this.gcAnalyzer = gcAnalyzer;
        this.outputStream = outputStream != null ? outputStream : System.out;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "helix-live-dashboard");
            t.setDaemon(true);
            return t;
        });
        this.running = false;
        this.activeClassLoaders = 1;
        this.cachedRulesCount = 0;
    }

    public synchronized void start(long refreshIntervalMs) {
        if (running) {
            log.warn("LiveDashboard already running.");
            return;
        }

        running = true;
        long interval = refreshIntervalMs > 0 ? refreshIntervalMs : 1000;
        scheduler.scheduleAtFixedRate(this::renderOnce, 0, interval, TimeUnit.MILLISECONDS);
        log.info("LiveDashboard started with {}ms refresh interval.", interval);
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        scheduler.shutdown();
        log.info("LiveDashboard stopped.");
    }

    public void renderOnce() {
        try {
            CompilationStats jitStats = jitMonitor != null ? jitMonitor.getStats() : null;
            GcStatistics gcStats = gcAnalyzer != null ? gcAnalyzer.getStatistics() : null;

            String dashboardText = renderer.renderDashboard(jitStats, gcStats, activeClassLoaders, cachedRulesCount);

            // Clear terminal screen ANSI sequence
            outputStream.print("\033[H\033[2J");
            outputStream.flush();
            outputStream.println(dashboardText);
        } catch (Exception e) {
            log.error("Error rendering LiveDashboard: {}", e.getMessage(), e);
        }
    }

    public void setActiveClassLoaders(long count) {
        this.activeClassLoaders = count;
    }

    public void setCachedRulesCount(long count) {
        this.cachedRulesCount = count;
    }

    public boolean isRunning() {
        return running;
    }
}
