package io.github.sineaggi.gradle.bannedwarnings.internal.deprecations;

import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;

/**
 * Starts recording deprecations when the configuration cache loads it.
 *
 * <p>On a configuration cache hit the settings plugin doesn't run, and build services are only created once a
 * task has finished, which is too late to see the first tasks' deprecations. Gradle loads the flow action's
 * parameters, which hold this object, before any task runs, so its {@code readResolve} is the earliest hook.
 */
public final class StartListeningOnLoad implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String rootDir;
    private final String userHome;

    public StartListeningOnLoad(String rootDir, String userHome) {
        this.rootDir = rootDir;
        this.userHome = userHome;
    }

    public String rootDir() {
        return rootDir;
    }

    @Serial
    private Object readResolve() throws ObjectStreamException {
        Deprecations.startListening(rootDir, userHome);
        return this;
    }
}
