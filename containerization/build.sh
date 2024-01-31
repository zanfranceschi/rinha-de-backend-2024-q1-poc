#!/usr/bin/bash

pushd ../
lein ring uberjar
docker build -t rinha-2024q1-crebito -t zanfranceschi/rinha-2024q1-crebito .
docker push zanfranceschi/rinha-2024q1-crebito
popd
