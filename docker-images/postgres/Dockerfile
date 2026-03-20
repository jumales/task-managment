FROM postgres:16-alpine

LABEL maintainer="demo"
LABEL description="PostgreSQL image for microservice demo"

ENV POSTGRES_USER=demo
ENV POSTGRES_PASSWORD=demo123
ENV POSTGRES_DB=postgres

COPY init.sql /docker-entrypoint-initdb.d/init.sql

EXPOSE 5432
