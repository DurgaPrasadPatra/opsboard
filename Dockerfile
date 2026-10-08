# syntax=docker/dockerfile:1

###############################################################################
# OpsBoard - multi-stage Dockerfile
#   Stage 1 (build):   Maven + JDK compile the code, run tests, produce the WAR
#   Stage 2 (runtime): slim Tomcat image that contains only the WAR
# The final image has no Maven, no source code, no build cache.
###############################################################################


############################ STAGE 1: BUILD ###################################

# Base image with Maven 3.9 and JDK 21. "AS build" names the stage so stage 2
# can copy files out of it.
FROM maven:3.9-eclipse-temurin-21 AS build

# All following commands run from /build (created automatically).
WORKDIR /build

# Copy ONLY pom.xml first. Docker caches each layer, so the dependency download
# below is re-run only when pom.xml changes, not on every code change.
COPY pom.xml .

# Download all dependencies and plugins into the image's Maven cache (~/.m2).
# -B = batch mode (no interactive prompts, cleaner CI logs).
RUN mvn -B dependency:go-offline

# Now copy the source code. This layer changes on every commit, but the
# dependency layer above stays cached.
COPY src ./src

# Build arguments let the pipeline pass metadata at build time:
#   docker build --build-arg GIT_COMMIT=abc1234 --build-arg BUILD_NUMBER=42 .
# Defaults make a plain "docker build ." still work.
ARG GIT_COMMIT=local
ARG BUILD_NUMBER=0
# Set to true if Jenkins already ran the tests and you want a faster image build.
ARG SKIP_TESTS=false

# Compile, run unit tests, and package target/opsboard.war.
# -Dgit.commit / -Dbuild.number are filtered into build.properties, which is
# what the dashboard and /api/info display.
# If a test fails, this step fails and no image is produced.
RUN mvn -B clean package \
      -Dgit.commit=${GIT_COMMIT} \
      -Dbuild.number=${BUILD_NUMBER} \
      -DskipTests=${SKIP_TESTS}


########################### STAGE 2: RUNTIME ##################################

# Tomcat 9 (Servlet 4 / javax.*) on JDK 21. Use tomcat:10.1-jdk21-temurin only
# if you migrate the code to jakarta.*.
FROM tomcat:9.0-jdk21-temurin

# Re-declare the args we want to use in THIS stage (args don't carry across
# stages), then record them as image labels so `docker inspect` shows what the
# image was built from.
ARG GIT_COMMIT=local
ARG BUILD_NUMBER=0
LABEL org.opencontainers.image.title="opsboard" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      build.number="${BUILD_NUMBER}"

# Create an unprivileged user (UID/GID 1001). The official Tomcat image runs as
# root by default; running as non-root limits damage if the app is compromised.
RUN groupadd -r -g 1001 tomcat \
 && useradd  -r -u 1001 -g tomcat -d /usr/local/tomcat -s /usr/sbin/nologin tomcat

# Folder where the deploy journal is stored. Mount a volume here to keep the
# data when the container is replaced. Owned by the tomcat user so it can write.
ENV APP_DATA_DIR=/data
RUN mkdir -p ${APP_DATA_DIR} \
 && chown -R tomcat:tomcat ${APP_DATA_DIR}

# Make container-aware JVM tuning the default: the heap is sized as 75% of the
# container's memory limit instead of the host's RAM.
# (CATALINA_OPTS applies to the Tomcat server JVM only.)
ENV CATALINA_OPTS="-XX:MaxRAMPercentage=75.0"

# Copy the WAR from the build stage into Tomcat's webapps folder. Tomcat unpacks
# and deploys it on startup. File name = context path: opsboard.war -> /opsboard/
# (name it ROOT.war to serve the app at /).
# --chown gives the tomcat user ownership so it can unpack the WAR.
COPY --from=build --chown=tomcat:tomcat /build/target/opsboard.war /usr/local/tomcat/webapps/opsboard.war

# Tomcat writes logs, temp files and the unpacked app under its home directory,
# so the non-root user must own it.
RUN chown -R tomcat:tomcat /usr/local/tomcat

# Declare /data as a volume mount point (documents the intent; a named volume
# is created automatically if you don't map one).
VOLUME ["/data"]

# Documentation only: Tomcat listens on 8080 inside the container.
# Publish it with -p <host-port>:8080 at run time.
EXPOSE 8080

# Docker runs this probe periodically. After 3 failures the container shows as
# "unhealthy" (visible in `docker ps`; ECS/Compose can act on it).
# /health returns 503 if the data dir isn't writable. start-period gives
# Tomcat time to boot before failures count.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fs http://localhost:8080/opsboard/health || exit 1

# Drop root privileges for everything from here on, including the running server.
USER tomcat

# Start Tomcat in the foreground (the base image's script). The container stays
# alive as long as this process runs, and logs go to stdout for `docker logs`.
CMD ["catalina.sh", "run"]
