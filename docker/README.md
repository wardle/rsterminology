# Instructions
download a snowmed release file and place into the docker/rsterminology it should be named along the lines uk_sct1cl_nnnnn.zip. If this filename needs to be changed then open the dockerfile and alter the line 
       `COPY uk_sct1cl_*.zip snomedCT.zip` 
#Running
In the docker directory run the command 
`docker-compose up`
