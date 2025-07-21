#!/usr/bin/env bash

# This script downloads the wasm file from the go-feature-flag repository and adds it to the build.

wasm_version="v1.45.4" # {{wasm_version}}
pwd
echo "Downloading go-feature-flag wasm version: $wasm_version"
ls "$(pwd)/wasm-releases"
echo "Downloading go-feature-flag wasm evaluation version: $wasm_version"
ls "$(pwd)/wasm-releases/evaluation"
mv ./wasm-releases/evaluation/gofeatureflag-evaluation_$wasm_version.wasi ./src/main/resources/wasm/gofeatureflag-evaluation_$wasm_version.wasi
