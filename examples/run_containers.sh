#!/bin/bash
set -euo pipefail

docker compose rm -f
docker compose up
