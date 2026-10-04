// Build and analysis pipeline for Jenkins.
// Requires the plugins "SonarQube Scanner for Jenkins" and "Pipeline Maven Integration", and in "Manage Jenkins":
//   - Tools: a Maven installation named 'Maven' and a JDK 25 installation named 'JDK25'
//   - System > SonarQube servers: a server named 'SonarQube' (with an authentication token credential)
//   - A SonarQube webhook to <jenkins>/sonarqube-webhook/ so the quality gate result is reported back
// Change the names below if your installations are named differently.
pipeline {
    agent any

    tools {
        maven 'Maven'
        jdk 'JDK25'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {
        stage('Build') {
            steps {
                mvn 'clean verify'
            }
        }

        stage('SonarQube analysis') {
            steps {
                // Sets the server url and token as environment variables for the scanner
                withSonarQubeEnv('SonarQube') {
                    mvn 'org.sonarsource.scanner.maven:sonar-maven-plugin:sonar'
                }
            }
        }

        stage('Quality gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
    }

    post {
        success {
            archiveArtifacts artifacts: 'target/areashop-*.jar', excludes: 'target/original-*.jar', fingerprint: true
        }
    }
}

def mvn(String goals) {
    if (isUnix()) {
        sh "mvn -B -ntp ${goals}"
    } else {
        bat "mvn -B -ntp ${goals}"
    }
}
