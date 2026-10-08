pipeline {
    agent any

    tools {
        maven 'maven3'
        jdk   'jdk21'
    }

    environment {
        CONTAINER   = 'opsboard'
        IMAGE       = 'tomcat:9.0-jdk21-temurin'
        APP_CONTEXT = 'opsboard'                 // opsboard.war -> /opsboard ; use ROOT to serve at /
        WEBAPPS_DIR = '/opt/tomcat/webapps'
        LOGS_DIR    = '/opt/tomcat/logs'
        DATA_DIR    = '/opt/tomcat/data'
        HOST_PORT   = '8081'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    triggers { githubPush() }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                }
            }
        }

        stage('Build & Test') {
            steps {
                sh 'mvn  clean package -Dgit.commit=$GIT_SHORT -Dbuild.number=$BUILD_NUMBER'
            }
            post {
                always { junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml' }
            }
        }

        stage('Archive') {
            steps { archiveArtifacts artifacts: 'target/*.war', fingerprint: true }
        }

        stage('Deploy') {
            steps {
                sh '''
                    set -e
                    WAR=$(ls target/*.war | head -n1)

                    # create the container once, with volume mapping
                    if ! docker ps -a --format '{{.Names}}' | grep -qw "$CONTAINER"; then
                      docker run -d --name "$CONTAINER" \
                        -p ${HOST_PORT}:8080 \
                        -v ${WEBAPPS_DIR}:/usr/local/tomcat/webapps \
                        -v ${LOGS_DIR}:/usr/local/tomcat/logs \
                        -v ${DATA_DIR}:/data \
                        --restart unless-stopped \
                        "$IMAGE"
                    fi
                    docker start "$CONTAINER" || true

                    # drop the WAR into the mapped folder -> Tomcat hot-deploys it
                    cp "$WAR" ${WEBAPPS_DIR}/${APP_CONTEXT}.war
                '''
            }
        }

        stage('Verify') {
            steps {
                sh '''
                    for i in $(seq 1 18); do
                      body=$(curl -fs http://localhost:${HOST_PORT}/${APP_CONTEXT}/api/info || true)
                      if echo "$body" | grep -q "$GIT_SHORT"; then
                        echo "Commit $GIT_SHORT is live."
                        exit 0
                      fi
                      echo "Waiting for new version ($i/18)..."
                      sleep 5
                    done
                    docker logs --tail 60 "$CONTAINER"
                    exit 1
                '''
            }
        }
    }

    post {
        success { echo "Deployed: http://<EC2-IP>:${HOST_PORT}/${APP_CONTEXT}/" }
        failure { echo 'Build or deploy failed. Check the console output.' }
    }
}
