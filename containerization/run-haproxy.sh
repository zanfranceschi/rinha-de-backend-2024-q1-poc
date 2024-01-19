#!/usr/bin/bash

docker-compose -f ./haproxy/docker-compose.yml down

pushd ../
lein ring uberjar
docker build -t rinha-2024q1-crebito -t zanfranceschi/rinha-2024q1-crebito .
docker push zanfranceschi/rinha-2024q1-crebito
popd

docker-compose -f ./haproxy/docker-compose.yml up