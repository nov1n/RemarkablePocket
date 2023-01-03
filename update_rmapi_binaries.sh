#!/bin/bash

# Usage: ./update_rmapi_binaries.sh <repo> <version>

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)

cd "${1}" || exit
git fetch
git checkout "${2}"

move() {
  mv rmapi "${SCRIPT_DIR}"/src/main/jib/usr/local/bin/rmapi_"${1}"
}

GOOS=linux GOARCH=arm64 go build && move "aarch64"
GOOS=linux GOARCH=arm GOARM=5 go build && move "arm"
GOOS=linux GOARCH=amd64 go build && move "amd64"

cd "${SCRIPT_DIR}" || exit
