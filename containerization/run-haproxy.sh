#!/usr/bin/bash

cp ./db/init.sql ./nginx/init.sql

docker-compose -f ./haproxy/docker-compose.yml down
docker-compose -f ./haproxy/docker-compose.yml up