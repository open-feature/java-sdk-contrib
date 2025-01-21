package dev.openfeature.contrib.providers.flagd.e2e;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.e2e.steps.Event;
import dev.openfeature.contrib.providers.flagd.e2e.steps.FlagSteps;
import dev.openfeature.contrib.providers.flagd.e2e.steps.ProviderType;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.MutableContext;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class State {
    public ProviderType providerType;
    public Client client;
    public List<Event> events = new LinkedList<>();
    public Optional<Event> lastEvent;
    public FlagSteps.Flag flag;
    public MutableContext context = new MutableContext();
    public FlagEvaluationDetails evaluation;
    public FlagdOptions options;
    public FlagdOptions.FlagdOptionsBuilder builder = FlagdOptions.builder();
    public static Config.Resolver resolverType;
}
