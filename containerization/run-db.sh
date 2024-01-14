#!/usr/bin/bash
docker-compose -f docker-compose-db.yml rm -f
docker-compose -f docker-compose-db.yml down --rmi all
docker-compose -f docker-compose-db.yml up
