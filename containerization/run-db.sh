#!/usr/bin/bash

docker-compose -f db/docker-compose.yml down
docker-compose -f db/docker-compose.yml up
