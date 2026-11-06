package dev.openfeature.contrib.providers.gofeatureflag.wasm;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * WasmGuestOutput turns what the evaluation engine writes to one of its standard streams into log
 * events, one per line, so that a host routes the engine's diagnostics, such as the panic it prints
 * before trapping, through its logging backend like the rest of the provider's.
 */
final class WasmGuestOutput extends OutputStream {
    private final Consumer<String> sink;
    private final ByteArrayOutputStream line = new ByteArrayOutputStream();

    WasmGuestOutput(final Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public synchronized void write(final int b) {
        if (b == '\n') {
            emit();
        } else if (b != '\r') {
            line.write(b);
        }
    }

    @Override
    public synchronized void close() {
        emit();
    }

    private void emit() {
        if (line.size() > 0) {
            sink.accept(line.toString(StandardCharsets.UTF_8));
            line.reset();
        }
    }
}
