#!/usr/bin/bash

(
    docker-compose -f ./db/docker-compose.yml rm -f
    docker-compose -f ./db/docker-compose.yml down --rmi all
    docker-compose -f ./nginx/docker-compose.yml rm -f
    docker-compose -f ./nginx/docker-compose.yml down --rmi all
    docker-compose -f ./haproxy/docker-compose.yml rm -f
    docker-compose -f ./haproxy/docker-compose.yml down --rmi all
    docker-compose -f ./nginx-hostmode/docker-compose.yml rm -f
    docker-compose -f ./nginx-hostmode/docker-compose.yml down --rmi all
    docker system prune -f
    docker rmi $(docker images -a -q)
)