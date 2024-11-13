#!/bin/bash

set -e

jpackage=${1}
appDirectory=${2}
jdkDirectory=${3}
resourcesDir=${4}
platform=${5}
version=${6}
output=${7}

function checkExitCode() {
    exitValue=${1}
    if [[ ${exitValue} -ne 0 ]]; then
        exit "${exitValue}"
    fi
}

jvmOptions="-Dfile.encoding=UTF-8 \
    -Dprism.maxvram=2G \
    --add-opens java.base/java.security=ALL-UNNAMED \
    --add-opens jdk.crypto.cryptoki/sun.security.pkcs11=ALL-UNNAMED"

arguments=(
    "--input" "${appDirectory}"
    "--runtime-image" "${jdkDirectory}"
    "--main-jar" "autogram-service.jar"
    "--app-version" "1.0.0"
    "--resource-dir" "./"
    "--dest" "${output}"
)

if [[ "$platform" == "linux" ]]; then
    lowercase_name="autogram-service"

    arguments+=(
        "--name" "${lowercase_name}"
        "--java-options" "${jvmOptions}"
    )

    $jpackage "${arguments[@]}"
    checkExitCode $?
fi
