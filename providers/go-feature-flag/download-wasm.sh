#!/usr/bin/env bash

# This script downloads the wasm file from the go-feature-flag repository and adds it to the build.

wasm_version="v1.45.0" # {{wasm_version}}
pwd
mv ./wasm-releases/evaluation/gofeatureflag-evaluation_$wasm_version.wasi ./src/main/resources/wasm/gofeatureflag-evaluation_$wasm_version.wasi
