# Example of file
```yaml
version "3.8"

services:
    backend:
        image: NAME_OF_MY_BACKEND_IMAGE
        build: # if we dont have it downloaded in docker-hub
            context: ./where/i/have/Dockerfile/for/it
        depends_on:
            db:
                condition: service_healthy # this example describes the "use health-check". else, it will use a "is container running?"
            search:
                condition: service_healthy
        environment:
            SMTH_COMMON_FOR_IT: IDK
             
    frontend:
        image: NAME_OF_FRONTEND_IMAGE
        build: 
            context: ./where/i/have/Dockerfile/for/it
        ports:
            -"8080:7070" # 8080 from host. 7070 internal in docker
        environment:
            SMTH_COMMON_FOR_IT: IDK
        depends_on:
            - backend

    db:
        image: postgres:16.0
        environment:
            POSTGRES_PASSWORD: ${DB_PASSWORD}
            POSTGRES_USER: ${DB_USER}
            POSTGRES_DB: ${DB_NAME}
        healthcheck:
            test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
            interval: 10s
            timeout: 5s
            retries: 5
        voluems:
            db-data:/var/lib/postgresql/data
    search:
        image: docker.elastic.co/elasticsearch/elasticsearch:7.17.22
        environment:
            discovery.type: single-node
        healthcheck:
            test: ["CMD", "curl", "-f", "http://localhost:9200/_cluster/health"]
            interval: 10s
            timeout: 5s
            retries: 5
        volumes:
            - es-data:/usr/share/elasticsearch/data
voluems:
    db-data:
    es-data:
```

for using env parametrs, you should create a .env file
```
DB_PASSWORD=123
DB_USER=Vlad
DB_NAME=db_for_vlad
```

# Run
- `docker compose`
    - `up` 
        - `--build`
    - `down`
        - `-v`
    - `logs`
    - `ps`

# Source
- https://www.youtube.com/watch?v=sXjkAEqFZEI&t=26s