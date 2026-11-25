package io.github.sineaggi.gradle.bannedwarnings.internal.deprecations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.gradle.api.internal.plugins.ApplyPluginBuildOperationType;
import org.gradle.configuration.ApplyScriptPluginBuildOperationType;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.operations.problems.ProblemGroup;
import org.gradle.operations.problems.ProblemLocation;
import org.gradle.operations.problems.ProblemUsageProgressDetails;
import org.gradle.operations.problems.StackTraceLocation;

/**
 * Listens to Gradle's build operations and records every deprecation, attributed to the plugin or script whose
 * application it happened in.
 *
 * <p>This uses Gradle's internal build operation events: the {@code org.gradle.operations} types are the contract
 * Gradle keeps for Develocity, which makes them the most stable internal surface, but not public API.
 */
public final class DeprecationCollector implements BuildOperationListener {

    private final Map<OperationIdentifier, BuildOperationDescriptor> running = new ConcurrentHashMap<>();
    private final List<DeprecationEvent> events = new ArrayList<>();
    private final Function<String, String> relativize;

    public DeprecationCollector(Function<String, String> relativize) {
        this.relativize = relativize;
    }

    @Override
    public void started(BuildOperationDescriptor descriptor, OperationStartEvent event) {
        running.put(descriptor.getId(), descriptor);
    }

    @Override
    public void finished(BuildOperationDescriptor descriptor, OperationFinishEvent event) {
        running.remove(descriptor.getId());
    }

    @Override
    public void progress(OperationIdentifier operation, OperationProgressEvent event) {
        if (!(event.getDetails() instanceof ProblemUsageProgressDetails problem) || !isDeprecation(problem.getDefinition().getGroup())) {
            return;
        }
        String frame = firstUserFrame(problem);
        String name = problem.getDefinition().getDisplayName() != null ? problem.getDefinition().getDisplayName() : problem.getDefinition().getName();
        String label = problem.getContextualLabel() != null ? problem.getContextualLabel() : name;
        DeprecationEvent recorded = new DeprecationEvent(name, label, source(operation, frame), frame);
        synchronized (events) {
            events.add(recorded);
        }
    }

    /** Returns and forgets the deprecations recorded so far. */
    public List<DeprecationEvent> drain() {
        synchronized (events) {
            List<DeprecationEvent> copy = List.copyOf(events);
            events.clear();
            return copy;
        }
    }

    private static boolean isDeprecation(ProblemGroup group) {
        for (ProblemGroup g = group; g != null; g = g.getParent()) {
            if ("deprecation".equalsIgnoreCase(g.getName())) {
                return true;
            }
        }
        return false;
    }

    /** The innermost non-core plugin or script being applied, else the class of the first frame outside Gradle. */
    private String source(OperationIdentifier operation, String frame) {
        OperationIdentifier id = operation;
        int guard = 0;
        while (id != null && guard++ < 1000) {
            BuildOperationDescriptor descriptor = running.get(id);
            if (descriptor == null) {
                break;
            }
            Object details = descriptor.getDetails();
            if (details instanceof ApplyPluginBuildOperationType.Details plugin && !isCore(plugin)) {
                return plugin.getPluginId() != null ? "plugin " + plugin.getPluginId() : "plugin class " + plugin.getPluginClass().getName();
            }
            if (details instanceof ApplyScriptPluginBuildOperationType.Details script && script.getFile() != null) {
                return "script " + relativize.apply(script.getFile());
            }
            id = descriptor.getParentId();
        }
        if (!frame.isEmpty()) {
            String className = frame.substring(0, frame.lastIndexOf('.', frame.indexOf('(') < 0 ? frame.length() : frame.indexOf('(')));
            int nested = className.indexOf('$');
            return "class " + (nested < 0 ? className : className.substring(0, nested));
        }
        return "unknown";
    }

    private static boolean isCore(ApplyPluginBuildOperationType.Details plugin) {
        String pluginClass = plugin.getPluginClass() == null ? "" : plugin.getPluginClass().getName();
        return pluginClass.startsWith("org.gradle.") || pluginClass.startsWith("io.github.sineaggi.gradle.bannedwarnings.")
                || (plugin.getPluginId() != null && plugin.getPluginId().startsWith("org.gradle."));
    }

    private static String firstUserFrame(ProblemUsageProgressDetails problem) {
        List<ProblemLocation> locations = new ArrayList<>(problem.getOriginLocations());
        locations.addAll(problem.getContextualLocations());
        for (ProblemLocation location : locations) {
            if (location instanceof StackTraceLocation stack) {
                for (StackTraceElement element : stack.getStackTrace()) {
                    if (!isFrameworkClass(element.getClassName())) {
                        return element.toString();
                    }
                }
            }
        }
        return "";
    }

    static boolean isFrameworkClass(String className) {
        return className.startsWith("org.gradle.") || className.startsWith("java.") || className.startsWith("javax.")
                || className.startsWith("jdk.") || className.startsWith("sun.") || className.startsWith("kotlin.")
                || className.startsWith("groovy.") || className.startsWith("org.codehaus.groovy.")
                || className.startsWith("com.sun.") || className.equals("Program")
                || className.startsWith("io.github.sineaggi.gradle.bannedwarnings.");
    }
}
