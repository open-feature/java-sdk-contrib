package dev.openfeature.contrib.providers.v2.gofeatureflag.wasm.bean;

import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.Flag;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WasmInput {
    private String flagKey;
    private Flag flag;
    private Map<String, Object> evalContext;
    private Map<String, Object> flagContext;
}
