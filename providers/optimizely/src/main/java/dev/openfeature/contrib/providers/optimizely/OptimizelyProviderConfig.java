package dev.openfeature.contrib.providers.optimizely;

import com.optimizely.ab.bucketing.UserProfileService;
import com.optimizely.ab.config.ProjectConfig;
import com.optimizely.ab.config.ProjectConfigManager;
import com.optimizely.ab.error.ErrorHandler;
import com.optimizely.ab.event.EventHandler;
import com.optimizely.ab.event.EventProcessor;
import com.optimizely.ab.odp.ODPManager;
import com.optimizely.ab.optimizelydecision.OptimizelyDecideOption;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Configuration for initializing statsig provider. */
@Getter
@Builder
public class OptimizelyProviderConfig {

    private ProjectConfigManager projectConfigManager;
    private EventHandler eventHandler;
    private EventProcessor eventProcessor;
    private String datafile;
    private ErrorHandler errorHandler;
    private ProjectConfig projectConfig;
    private UserProfileService userProfileService;
    private List<OptimizelyDecideOption> defaultDecideOptions;
    private ODPManager odpManager;
}
