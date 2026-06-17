# Examples of commands
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

# Examples of files
## Dockerfile
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

## .dockerignore
```
.git
node_modules
Dockerfile
.idea
```

## Makefile
```
name_of_command:
    <command>
```
how to run: 'make name_of_command'

# Volumes
есть Аннонимные(удаляется, когда и контейнер удаляется) и Именнованные

# Sources
- `https://www.youtube.com/watch?v=n9uCgUzfeRQ`
- `https://habr.com/ru/companies/slurm/articles/528206/`
- `https://uproger.com/100-voprosov-so-sobesedovanij-po-docker-s-podrobnymi-otvetami/`