# Production-style variant: bake the WAR into an image (for ECR -> ECS/EKS).
# Build the WAR first:  mvn -B clean package
FROM tomcat:9.0-jdk17-temurin
COPY target/opsboard.war /usr/local/tomcat/webapps/opsboard.war
ENV APP_DATA_DIR=/data
VOLUME /data
EXPOSE 8080
