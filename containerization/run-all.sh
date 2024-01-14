#!/usr/bin/bash
docker-compose -f docker-compose-all.yml rm -f
docker-compose -f docker-compose-all.yml down --rmi all
# docker system prune -f
#pushd ../
#lein uberjar
#docker build -t rinha-concurrency-control .
#popd
docker-compose -f docker-compose-all.yml up