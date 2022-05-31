#!/bin/bash

KOTLIN_LIB=$(realpath "${HOME}"/projects/kotlin/dist/kotlinc/lib)
ANDROID_BUILD_TOOLS=$(realpath "${HOME}"/Android/Sdk/build-tools/32.0.0)
JDK_11="/usr/local/buildtools/java/jdk11/"

KOTLIN_DEX=$(realpath kotlin-compiler-dex.zip)

"${ANDROID_BUILD_TOOLS}"/d8 --file-per-class --force-ea --output "${KOTLIN_DEX}" --lib $JDK_11 \
  --classpath "${KOTLIN_LIB}"/kotlin-stdlib.jar \
  --classpath "${KOTLIN_LIB}"/kotlin-stdlib-jdk8.jar \
  --classpath "${KOTLIN_LIB}"/kotlin-preloader.jar \
  --min-api 30 "${KOTLIN_LIB}"/kotlin-compiler.jar

mkdir kotlin-compiler-jvm
pushd kotlin-compiler-jvm || exit
unzip "${KOTLIN_LIB}"/kotlin-compiler.jar
popd || exit

mkdir kotlin-compiler-dex
pushd kotlin-compiler-dex || exit
unzip "${KOTLIN_DEX}"
rm "${KOTLIN_DEX}"
popd || exit
