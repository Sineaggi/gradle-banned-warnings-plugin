package io.github.sineaggi.gradle.bannedwarnings.internal;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;

/** Task action that starts recording a compile task's output. */
public final class StartCapture implements Action<Task> {

    private final Provider<CaptureService> service;

    public StartCapture(Provider<CaptureService> service) {
        this.service = service;
    }

    @Override
    public void execute(Task task) {
        service.get().start(task);
    }
}
