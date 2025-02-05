#!/bin/bash

# $1: The tag to update to, or latest if undefined

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)

git clone git@github.com:ddvk/rmapi.git
cd rmapi || exit
git fetch
git checkout "${1:-$(git tag --sort=-creatordate | head -n 1)}"

move() {
  mv rmapi "${SCRIPT_DIR}"/src/main/jib/usr/local/bin/rmapi_"${1}"
}

GOOS=linux GOARCH=arm64 go build && move "aarch64"
GOOS=linux GOARCH=arm GOARM=5 go build && move "arm"
GOOS=linux GOARCH=amd64 go build && move "amd64"
GOOS=darwin GOARCH=arm64 go build && move "arm64"

cd - || exit
rm -rf rmapi
