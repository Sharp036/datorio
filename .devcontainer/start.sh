#!/bin/sh
# Runs on every start of the codespace: starts the stack with the Airflow example and loads the examples.
set -e
cd "$(dirname "$0")/.."
[ -f .env ] || cp .env.example .env

# The Docker daemon of the codespace may still be starting
for i in $(seq 1 60); do docker info >/dev/null 2>&1 && break; sleep 2; done

# Containers of the previous start can be left in a state Docker cannot start again ("container with
# given ID already exists"), so they are created anew; the data stays in the volumes
docker compose -f docker-compose.yml -f examples/airflow/compose.yml -f .devcontainer/compose.codespace.yml \
  up -d --force-recreate --remove-orphans
# An updated image leaves the previous one unused
docker image prune -f >/dev/null

python3 examples/load_examples.py
