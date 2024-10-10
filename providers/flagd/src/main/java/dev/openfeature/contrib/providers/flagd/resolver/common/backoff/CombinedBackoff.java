package dev.openfeature.contrib.providers.flagd.resolver.common.backoff;

import lombok.Getter;

/**
 * A backoff strategy that combines multiple backoff strategies.
 * The strategy starts with the first provided strategy and will switch to the next backoff strategy in the list when
 * the current one is exhausted.
 */
public class CombinedBackoff implements BackoffStrategy {
    private final BackoffStrategy[] backoffStrategies;
    private int currentStrategyIndex;

    @Getter
    private BackoffStrategy currentStrategy;

    /**
     * Creates a new combined backoff strategy.
     * The strategy starts with the first provided strategy and will switch to the next backoff strategy in the list
     * when the current one is exhausted.
     *
     * @param backoffStrategies the list of backoff strategies to combine
     */

    public CombinedBackoff(BackoffStrategy[] backoffStrategies) {
        this.backoffStrategies = backoffStrategies.clone();
        this.currentStrategyIndex = 0;
        this.currentStrategy = this.backoffStrategies[currentStrategyIndex];
        updateCurrentStrategy();
    }

    @Override
    public long getCurrentBackoffMillis() {
        return currentStrategy.getCurrentBackoffMillis();
    }

    @Override
    public boolean isExhausted() {
        updateCurrentStrategy();
        return currentStrategy.isExhausted();
    }

    @Override
    public void nextBackoff() {
        updateCurrentStrategy();
        currentStrategy.nextBackoff();
    }

    /**
     * Switches to the next backoff strategy if the current one is exhausted.
     */
    private void updateCurrentStrategy() {
        // Move to the next non-exhausted strategy if the current one is exhausted
        while (!isLastStrategy() && currentStrategy.isExhausted()) {
            currentStrategyIndex++;
            currentStrategy = backoffStrategies[currentStrategyIndex];
        }
    }

    private boolean isLastStrategy() {
        return currentStrategyIndex + 1 >= backoffStrategies.length;
    }

    @Override
    public void reset() {
        for (int i = 0; i <= currentStrategyIndex; i++) {
            backoffStrategies[i].reset();
        }

        currentStrategyIndex = 0;
        currentStrategy = backoffStrategies[currentStrategyIndex];
    }
}
