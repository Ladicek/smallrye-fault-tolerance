package io.smallrye.faulttolerance.core.stopwatch;

public class SystemStopwatch implements Stopwatch {
    @Override
    public RunningStopwatch start() {
        long start = System.nanoTime();

        return () -> {
            long now = System.nanoTime();
            return (now - start) / 1_000_000;
        };
    }
}
