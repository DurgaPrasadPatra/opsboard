# OpsBoard

A small Tomcat 9 web app built for practising **GitHub -> Jenkins -> WAR -> Docker/Tomcat** pipelines on AWS.
It shows what is running (build/commit/build number), where (host or container, Java, Tomcat, memory),
and has a **deploy journal** that persists to a mapped volume, so you can *prove* your volume mapping works.

## Endpoints

| URL | Purpose |
|---|---|
| `/opsboard/` | Dashboard UI |
| `/opsboard/health` | `200 {"status":"UP"}`; `503` if the data dir isn't writable |
| `/opsboard/api/info` | Build, runtime and storage details (JSON) |
| `/opsboard/api/notes` | `GET` list, `POST text=...` add, `DELETE` clear |

## Stack

Java 21, Maven (WAR), Servlet 4.0 (`javax.*`) -> **Tomcat 9**, JUnit 5. No other dependencies.
For Tomcat 10+, switch to `jakarta.servlet-api` and `jakarta.servlet.*` imports and use the `tomcat:10.1-jdk21-temurin` image.

## Run locally

```bash
mvn -B clean package          # runs tests, produces target/opsboard.war
docker run --rm -p 8081:8080 -v $(pwd)/target/opsboard.war:/usr/local/tomcat/webapps/opsboard.war tomcat:9.0-jdk21-temurin
# open http://localhost:8081/opsboard/
```

## How build info gets in

`pom.xml` filters `src/main/resources/build.properties`. The Jenkinsfile passes
`-Dgit.commit=$GIT_SHORT -Dbuild.number=$BUILD_NUMBER`, so each deployed WAR reports the commit that built it.
The **Verify** stage polls `/api/info` until it sees the new commit, so a green build means the new version is actually live.

## EC2 one-time setup (Jenkins + Docker on one host)

```bash
sudo mkdir -p /opt/tomcat/webapps /opt/tomcat/logs /opt/tomcat/data
sudo chown -R jenkins:jenkins /opt/tomcat/webapps
```

Security group: 8080 (Jenkins) and 8081 (app) from your IP only.
Jenkins: tools `maven3` and `jdk21`, Pipeline from SCM, Script Path `Jenkinsfile`, GitHub webhook `http://<EC2-IP>:8080/github-webhook/`.

## Test the volume mapping

1. Open the dashboard, add a few journal entries.
2. Push a code change -> pipeline redeploys -> entries are still there.
3. `docker rm -f opsboard`, run the pipeline again -> entries are still there (they live in `/opt/tomcat/data` on the host).
4. Remove `-v ${DATA_DIR}:/data` from the pipeline, recreate the container, and the entries vanish after removal. That is the difference a mount makes.

Note: mounts are fixed at container creation. If you change a `-v` line, run `docker rm -f opsboard` first.

## Separate Jenkins and Docker hosts

Deploy stage must get the WAR to the Docker host (SCP, Jenkins agent on that host, or EFS). The mount path is always on the Docker host.

## Production variant

`Dockerfile` bakes the WAR into an image: build, tag, push to ECR, and run on ECS/EKS. No bind mounts for the code; keep `/data` on EFS/EBS.

## Rollback

Jenkins archives every WAR. Copy an older one to `/opt/tomcat/webapps/opsboard.war`.
