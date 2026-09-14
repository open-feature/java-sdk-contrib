package dev.openfeature.contrib.providers.gofeatureflag.wasm.bean;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * This class represents the input to the WASM module.
 * It contains the flag key, the flag, the evaluation context, and the flag context.
 * The flag is passed through as the raw JSON received from the relay proxy, so that no field the
 * engine understands can be lost on the way in.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WasmInput {
    private String flagKey;
    private JsonNode flag;
    private Map<String, Object> evalContext;
    private FlagContext flagContext;
}
