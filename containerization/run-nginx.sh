#!/usr/bin/bash
docker-compose -f ./nginx/docker-compose.yml rm -f
docker-compose -f ./nginx/docker-compose.yml down --rmi all
# docker system prune -f
pushd ../
lein uberjar
docker build -t rinha-2024q1-crebito .
popd
docker-compose -f ./nginx/docker-compose.yml up