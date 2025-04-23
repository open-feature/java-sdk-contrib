package dev.openfeature.contrib.providers.gofeatureflag.wasm.bean;

import dev.openfeature.contrib.providers.gofeatureflag.bean.Flag;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WasmInput {
    private String flagKey;
    private Flag flag;
    private Map<String, Object> evalContext;
    private FlagContext flagContext;
}
