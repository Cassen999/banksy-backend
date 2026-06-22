#!/bin/bash
if [ "$(docker ps -q -f name=banksy-db)" ]; then
    echo "banksy-db is already running"
elif [ "$(docker ps -aq -f name=banksy-db)" ]; then
    docker start banksy-db && echo "banksy-db started"
else
    docker run --name banksy-db \
        -e POSTGRES_PASSWORD=password \
        -e POSTGRES_DB=banksy \
        -p 5432:5432 \
        -v banksy-db-data:/var/lib/postgresql \
        -d postgres
    echo "banksy-db created and started"
fi
