#!/usr/bin/bash

docker-compose -f ./nginx-hostmode/docker-compose.yml down

pushd ../
lein ring uberjar
docker build -t rinha-2024q1-crebito -t zanfranceschi/rinha-2024q1-crebito .
docker push zanfranceschi/rinha-2024q1-crebito
popd

docker-compose -f ./nginx-hostmode/docker-compose.yml up