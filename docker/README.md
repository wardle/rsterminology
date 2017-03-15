# Instructions
download a snowmed release file and place into the docker/rsterminology it should be named along the lines uk_sct1cl_nnnnn.zip. If this filename needs to be changed then open the dockerfile and alter the line
       `COPY uk_sct1cl_*.zip snomedCT.zip`

After building the server target jar copy the jar into the directory with the name rsterminology-server.jar.

## Running

In the docker directory run the command
`docker-compose up -d`

## Interactive
`docker exec -it docker_rsterminology_1 java -jar rsterminology-server.jar --browser`
