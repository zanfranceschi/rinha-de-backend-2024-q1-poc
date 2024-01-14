#!/usr/bin/bash

(
    docker-compose -f docker-compose-db.yml rm -f
    docker-compose -f docker-compose-db.yml down --rmi all
    docker-compose -f docker-compose-all.yml rm -f
    docker-compose -f docker-compose-all.yml down --rmi all
    docker system prune -f
    # docker rmi $(docker images -a -q)
)