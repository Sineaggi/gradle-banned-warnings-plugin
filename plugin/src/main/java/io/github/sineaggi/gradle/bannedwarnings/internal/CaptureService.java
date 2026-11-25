package io.github.sineaggi.gradle.bannedwarnings.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.gradle.api.Task;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/** Records the console output of compile tasks while they run. */
public abstract class CaptureService implements BuildService<BuildServiceParameters.None> {

    public static final String NAME = "bannedWarningsCapture";

    private final Map<String, Capture> captures = new ConcurrentHashMap<>();

    public void start(Task task) {
        Capture capture = new Capture(task.getLogger().isWarnEnabled());
        task.getLogging().addStandardOutputListener(capture);
        task.getLogging().addStandardErrorListener(capture);
        captures.put(task.getPath(), capture);
    }

    /** Returns the output captured since {@link #start}, or {@code null} if warnings were not visible. */
    public String finish(Task task) {
        Capture capture = captures.remove(task.getPath());
        if (capture == null) {
            return "";
        }
        task.getLogging().removeStandardOutputListener(capture);
        task.getLogging().removeStandardErrorListener(capture);
        return capture.warnEnabled ? capture.text() : null;
    }

    private static final class Capture implements StandardOutputListener {
        private final boolean warnEnabled;
        private final StringBuilder output = new StringBuilder();

        Capture(boolean warnEnabled) {
            this.warnEnabled = warnEnabled;
        }

        @Override
        public synchronized void onOutput(CharSequence chunk) {
            output.append(chunk);
        }

        synchronized String text() {
            return output.toString();
        }
    }
}
