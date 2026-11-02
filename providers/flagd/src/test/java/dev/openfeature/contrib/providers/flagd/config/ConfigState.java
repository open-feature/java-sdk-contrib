package dev.openfeature.contrib.providers.flagd.config;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;

/** Scenario state shared by the config steps, one instance per scenario. */
public class ConfigState {
    public FlagdOptions options;
    public FlagdOptions.FlagdOptionsBuilder builder = FlagdOptions.builder();
    public boolean hasError;
}
