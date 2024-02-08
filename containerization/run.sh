#!/usr/bin/bash

# exemplo de uso: `./run.sh nginx`

docker compose -f ./$1/docker-compose.yml down
docker compose -f ./$1/docker-compose.yml up