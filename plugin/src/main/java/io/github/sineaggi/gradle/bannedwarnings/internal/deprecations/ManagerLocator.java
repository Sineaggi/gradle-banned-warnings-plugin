package io.github.sineaggi.gradle.bannedwarnings.internal.deprecations;

import java.lang.reflect.Field;

import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.operations.BuildOperationListenerManager;

/**
 * Finds Gradle's build operation listener manager without the settings plugin, for a configuration cache hit
 * in a fresh daemon.
 *
 * <p>Gradle gives {@link DeprecationLogger}, which is static, the current build's progress event emitter when a
 * build starts. That emitter sends to the listener manager's broadcaster, an inner class of the manager. This
 * follows those private fields, so it depends on Gradle's implementation and returns null if any step isn't
 * what it expects.
 */
final class ManagerLocator {

    private ManagerLocator() {
    }

    static BuildOperationListenerManager find() {
        try {
            Object handler = read(DeprecationLogger.class, null, "DEPRECATED_FEATURE_HANDLER");
            Object emitter = read(handler.getClass(), handler, "progressEventEmitter");
            Object broadcaster = read(emitter.getClass(), emitter, "listener");
            Object manager = read(broadcaster.getClass(), broadcaster, "this$0");
            return manager instanceof BuildOperationListenerManager m ? m : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object read(Class<?> type, Object target, String name) throws ReflectiveOperationException {
        if (target == null && !type.equals(DeprecationLogger.class)) {
            throw new NoSuchFieldException(name);
        }
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(target);
        if (value == null) {
            throw new NoSuchFieldException(name + " is null");
        }
        return value;
    }
}
