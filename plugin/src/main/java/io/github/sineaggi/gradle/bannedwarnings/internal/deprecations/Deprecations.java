package io.github.sineaggi.gradle.bannedwarnings.internal.deprecations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.github.sineaggi.gradle.bannedwarnings.internal.CheckSettings;
import org.gradle.internal.operations.BuildOperationListenerManager;

/**
 * Daemon-wide registry of the deprecation collectors of running builds, keyed by build root directory.
 *
 * <p>Gradle's build operation listener manager can't be injected into build services or flow actions, and it
 * can't be serialized. So the settings plugin stores it here whenever the build is configured, and a
 * configuration cache hit, where the settings plugin doesn't run, reuses it. The manager lives as long as the
 * daemon, and Gradle reuses this plugin's class loader between builds of the same project, so it is there on
 * every cache hit except the first build of a fresh daemon. That one falls back to {@link ManagerLocator}.
 */
public final class Deprecations {

    private static final AtomicReference<BuildOperationListenerManager> MANAGER = new AtomicReference<>();
    private static final Map<String, DeprecationCollector> COLLECTORS = new ConcurrentHashMap<>();

    private Deprecations() {
    }

    public static void rememberManager(BuildOperationListenerManager manager) {
        MANAGER.set(manager);
    }

    /** Starts recording for the build at {@code rootDir}, unless already recording. Returns false if it can't. */
    public static boolean startListening(String rootDir, String userHome) {
        BuildOperationListenerManager manager = MANAGER.get();
        if (manager == null) {
            manager = ManagerLocator.find();
            if (manager == null) {
                return false;
            }
            MANAGER.compareAndSet(null, manager);
        }
        BuildOperationListenerManager listenerManager = manager;
        COLLECTORS.computeIfAbsent(rootDir, dir -> {
            CheckSettings paths = new CheckSettings(List.of(), dir, userHome, "", false);
            DeprecationCollector collector = new DeprecationCollector(paths::relativize);
            listenerManager.addListener(collector);
            return collector;
        });
        return true;
    }

    public static boolean isListening(String rootDir) {
        return COLLECTORS.containsKey(rootDir);
    }

    /** Returns and forgets what was recorded so far, and keeps recording. */
    public static List<DeprecationEvent> drain(String rootDir) {
        DeprecationCollector collector = COLLECTORS.get(rootDir);
        return collector == null ? List.of() : collector.drain();
    }

    /** Stops recording at the end of the build; the listener manager outlives the build. */
    public static void stopListening(String rootDir) {
        DeprecationCollector collector = COLLECTORS.remove(rootDir);
        BuildOperationListenerManager manager = MANAGER.get();
        if (collector != null && manager != null) {
            manager.removeListener(collector);
        }
    }
}
