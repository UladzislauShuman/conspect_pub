# Docker

## Введение

Тут всякое о Docker и Docker Compose

## Содержание
- и так далее
- в конце источники

## Немного основ

### Контейнеризация и Виртуализация
Есть Контейнеризация, а есть Виртуализация
- изоляция
    - у К она программная. у В аппаратная
- Операционка 
    - у В своя ОС. К же делят одну Хостовую ОС 
- Ресурсы
    - В потребляет реально много
- Скорость запуска
    - К быстрее
- Безопасность
    - у В она выше, но не сказал бы что прям критично
- Портативность
    - К лучше

## Основные сущности

Образ -- описание контейнера. Содержит все, что нужно для запуска.

Контейнер -- делается на основе образа

Механизмы
- Docker Platform -- это программа, что даёт возможность упаковывать приложения и запускать их на серверах
- Docker Engine -- клиент-серверное приложение
- Docker Client -- средство для взаимодействия с ним (Docker CLI, например)
- Docker Daemon -- это сервер Docker, который ожидает запросов к API Docker. Демон Docker управляет образами, контейнерами, сетями и томами.
- Docker Volumes -- это механизм для хранения данных
- Docker Registry -- удаленная платформа для хранения образов
- Docker Hub -- самый крупные реестр 
- Docker Repository -- набор образов, что имеют разыне версии просто
- Docker Service -- инструмент для управления группой одинаковых контейнеров в режиме Docker Swarm. позволяет описать состояние, а далее Docker будет сам его поддерживать (обновления, самовостановление и прочее)
- Docker Swarm -- для оркестрации

## Examples of commands
Mostly, where you see a NAME, also it can be ID of container/image/...

And also `image`, `container` and `volume` (and may be more, idk) have some comon commands (`rm`, `prune`, `ls` and etc)

- `docker pull NAME_FROM_DOCKER_HUB`
- `docker build -t NAME_OF_IMAGE:TAG .`
- `docker run` 
    - `-d`
    - `-p 3000:3000`
    - `-v name_of_volume:/PATH/TO/IT`. иначе, `-v /PATH/TO/IT` если написать так, то он будет аннонимным.
    - `-v "/Users/vladsuman/LEARN/project/:/app" -v /app/node_modules` -- теперь, когда мы поменяли где-то код(?), он будет автоматически ставиться в контейнер
    - `-e PORT=4200 -e......` или как вариант работать с env `-env-file ./PATH/.env`
    - `--rm` 
    - `--name CONTAINER_NAME`
    `IMAGE_NAME`
    
- `docker container ls`
- `docker image` 
    - `ls`
    - `inspect IMAGE_NAME`
    - `prune` -- delete all
- `docker images`
- `docker stop CONTAINER_NAME`
- `docker start CONTAINER_NAME`
- `docker rm CONTAINER_NAME`
- `docker rmi IMAGE_NAME`
- `docker attach CONTAINER_NAME` (?)
- `docker logs CONTAINER_NAME`
- `docker tag IMAGE_NAME NAME_OF_COPY_OF_THIS_IMAGE`
- `docker volume`
    - `ls`
    - `inspect VOLUME_NAME`

## Examples of files
### Dockerfile
```dockerfile
FROM node

WORKDIR /app

COPY package.json /app
RUN npm install

COPY . .

ENV PORT 4200

EXPOSE $PORT ## smth what i want

VOLUME [ "/app/data" ]

CMD ["node", "app.js"]
```

важно -- делать Поэтапную сборку. иначе образ будет весить очень много
```dockerfile
# BUILD STAGE
FROM golang:1.16 AS build
WORKDIR /go/src/app
COPY . .
RUN go build -o myapp

# RUN STAGE
FROM alpine:latest
WORKDIR /root/
COPY --from=build /go/src/app/myapp .
CMD ["./myapp"]
```

### .dockerignore
```
.git
node_modules
Dockerfile
.idea
```

### Makefile
```
name_of_command:
    <command>
```
how to run: 'make name_of_command'


## Docker Hub

чего-то интересного я вряд ли скажу

ну то есть -- это место, где можно найти нужный образ или загрузить свой

так же вероятно у образа есть версия alpine или slim -- либо очень маленькая система, либо меньше лишнего (например, документации)

## Сети

Есть разные драйверы. остановимся на bridge, host, overlay

### Bridge

по умолчанию

для общения между контейнерами и хост-ом

При запуске создается виртуальный интерфейс и подключается к мосту, предоставляя контейнерам IP-адреса из определенного диапазона. Bridge-сеть позволяет изолировать контейнеры от других сетевых интерфейсов хост-машины.

```cmd
docker network create --driver bridge app_network
docker run -d --network app_network --name app nginx
```

### Host

В этом режиме контейнер использует сетевой стек хост-машины. Это означает, что контейнер и хост имеют общий IP-адрес и порты. Host-сеть полезна для уменьшения сетевой задержки, однако она уменьшает изоляцию между контейнером и хостом.

```cmd
docker run -d --network host nginx
```

### Overlay

Overlay-сети дает контейнерам, что работают на разных физических или виртуальных машинах, общаться друг с другом будто они находятся на одной сети. (За счет создания распределенной сети поверх существующей физической инфраструктуры)

```cmd
docker network create --driver overlay --subnet 10.0.9.0/24 my_overlay_network
```

После подключения к одной сети, контейнеры могут общаться друг с другом по именам хоста: `docker exec container2 ping container1`. Это происходит засчет встроенного DNS-сервиса Docker.

## Volumes и Bind mounts

механизмы для работы с данными в контейнерах

### Volumes

нужен, чтобы хранить данные контейнера (например -- удалить контейнер, но данные сохраняться)

### Bind mounts

представляет собой просто монтирование директорий с хоста в директории внутри контейнера, что позволяет иметь прямой доступ к данным на хосте

при его использовании, Docker не управляет содержимым целевой директории, что означает, что изменения в файлах на хосте будут видны внутри контейнера сразу и наоборот

## Docker Compose

для удобной работы с несколькими котейнерами

с ним можно
- декларативно описать процесс
- управлять всеми контейнерами через команды
- ну и их жизненным циклом

### Example of file
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

### Run
- `docker compose`
    - `up` 
        - `--build`
    - `down`
        - `-v`
    - `logs`
    - `ps`


## Sources

без комментариев

- [статья. Изучаем докер. 6 частей](https://habr.com/ru/companies/ruvds/articles/438796/)
- [статья. Почти все, что вы хотели бы знать про Docker](https://habr.com/ru/articles/822707/)
- [видео. Docker за 2 часа](https://www.youtube.com/watch?v=n9uCgUzfeRQ)
- [вопросы. 50 вопросов в ответами](https://habr.com/ru/companies/slurm/articles/528206/)
- [вопросы. 100 вопросов в ответами](https://uproger.com/100-voprosov-so-sobesedovanij-po-docker-s-podrobnymi-otvetami/)
- [видео. Docker-compose за 15 минут](https://www.youtube.com/watch?v=sXjkAEqFZEI&t=26s)