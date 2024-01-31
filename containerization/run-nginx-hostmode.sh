#!/usr/bin/bash

cp ./db/init.sql ./nginx/init.sql

docker-compose -f ./nginx-hostmode/docker-compose.yml down
docker-compose -f ./nginx-hostmode/docker-compose.yml up