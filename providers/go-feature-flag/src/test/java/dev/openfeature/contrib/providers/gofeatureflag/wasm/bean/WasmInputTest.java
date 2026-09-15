package dev.openfeature.contrib.providers.gofeatureflag.wasm.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WasmInputTest {
    @SneakyThrows
    @DisplayName("the flag should reach the engine exactly as it was received")
    @Test
    void theFlagShouldReachTheEngineExactlyAsItWasReceived() {
        // a flag carrying a field no version of this provider ever modelled, and an explicit null
        val rawFlag = "{\"variations\":{\"on\":true},\"defaultRule\":{\"variation\":\"on\"},"
                + "\"nullField\":null,\"aFieldFromANewerEngine\":{\"nested\":[1,2,3]}}";

        val wasmInput = WasmInput.builder()
                .flagKey("TEST")
                .flag(Const.DESERIALIZE_OBJECT_MAPPER.readTree(rawFlag))
                .flagContext(FlagContext.builder().defaultSdkValue(false).build())
                .build();

        val serialized = new String(Const.SERIALIZE_WASM_MAPPER.writeValueAsBytes(wasmInput));
        val flagSentToEngine =
                Const.DESERIALIZE_OBJECT_MAPPER.readTree(serialized).get("flag");

        // byte-for-byte equivalent: no field dropped, reordered into a lossy model or reconstructed,
        // and the NON_NULL serialisation inclusion does not strip nulls inside the opaque flag.
        assertEquals(Const.DESERIALIZE_OBJECT_MAPPER.readTree(rawFlag), flagSentToEngine);
    }
}
