package dev.openfeature.contrib.testclasses;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.Getter;

@Getter
public class TestExecutor implements Executor {
    private final List<Runnable> tasks = new ArrayList<>();

    @Override
    public void execute(Runnable command) {
        tasks.add(command);
        new Thread(command).start();
    }
}
