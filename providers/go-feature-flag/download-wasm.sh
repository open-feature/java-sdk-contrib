#!/usr/bin/env bash

# This script copy the wasm module from the git submodule repository and adds it to the build.
wasm_version="v1.45.4" # {{wasm_version}}
mv ./wasm-releases/evaluation/gofeatureflag-evaluation_$wasm_version.wasi ./src/main/resources/wasm/gofeatureflag-evaluation_$wasm_version.wasi
