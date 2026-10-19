package org.minecraftsmp.dynamicshop.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Routes work to the thread that owns it. On Folia this uses the region, entity,
 * global and async schedulers; on Paper and Spigot it keeps the Bukkit scheduler.
 */
public final class FoliaScheduler {

    private static final boolean FOLIA = hasClass(
            "io.papermc.paper.threadedregions.RegionizedServer");

    private FoliaScheduler() {
    }

    /** A scheduled task that can be cancelled regardless of the scheduler behind it. */
    public interface Task {
        void cancel();

        boolean isCancelled();
    }

    /** Runs on the thread that owns the entity; skipped if the entity is removed first. */
    public static void runForEntity(Plugin plugin, Entity entity, Runnable task) {
        runForEntityLater(plugin, entity, task, 0L);
    }

    /** Runs on the thread that owns the entity after the delay in ticks. */
    public static void runForEntityLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (!FOLIA) {
            runBukkit(plugin, task, delayTicks);
            return;
        }
        if (delayTicks <= 0) {
            entity.getScheduler().run(plugin, scheduled -> task.run(), null);
        } else {
            entity.getScheduler().runDelayed(plugin, scheduled -> task.run(), null, delayTicks);
        }
    }

    /** Runs on the global region thread, used for server-wide state. */
    public static void runGlobal(Plugin plugin, Runnable task) {
        runGlobalLater(plugin, task, 0L);
    }

    /** Runs on the global region thread after the delay in ticks. */
    public static void runGlobalLater(Plugin plugin, Runnable task, long delayTicks) {
        if (!FOLIA) {
            runBukkit(plugin, task, delayTicks);
            return;
        }
        if (delayTicks <= 0) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduled -> task.run(), delayTicks);
        }
    }

    /** Repeats on the global region thread. */
    public static Task runGlobalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (!FOLIA) {
            return wrap(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
        }
        return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin, scheduled -> task.run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks)));
    }

    /** Runs off the server threads. */
    public static void runAsync(Plugin plugin, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return;
        }
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    /** Repeats off the server threads; ticks are converted at 50 ms each. */
    public static Task runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (!FOLIA) {
            return wrap(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks));
        }
        return wrap(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduled -> task.run(),
                Math.max(1L, delayTicks) * 50L, Math.max(1L, periodTicks) * 50L, TimeUnit.MILLISECONDS));
    }

    /** Calls the task on the global region thread and returns its result. */
    public static <T> CompletableFuture<T> callGlobal(Plugin plugin, Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runGlobal(plugin, () -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private static void runBukkit(Plugin plugin, Runnable task, long delayTicks) {
        if (delayTicks <= 0) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    private static Task wrap(BukkitTask task) {
        return new Task() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }

    private static Task wrap(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
        return new Task() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }

    private static boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
