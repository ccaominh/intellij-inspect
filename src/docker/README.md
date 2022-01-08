# Generating `jdk.table.xml`

1. Build docker image with X11: `gw dockerBuildX11`
2. Start X11, e.g.: `open -a XQuartz`
3. Get IP address: `IP=$(ifconfig en0 | awk '$1=="inet" {print $2}')`
4. Allow connections to X server: `xhost +$IP`   
5. Start docker image: `docker run --rm -v $(pwd):/project -it --entrypoint /bin/ash -e DISPLAY=$IP:0 intellij-inspect:x11`
7. Start IntelliJ by running `~/intellij/bin/idea.sh` and create dummy project
8. Copy generated `~/.IdeaIC20xx.x/config/options/jdk.table.xml` out of docker (adjust IntelliJ version)
9. Disallow connections to X server: `xhost -$IP`

