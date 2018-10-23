pipeline {
    agent none
    stages {
        stage('build') {
            agent any

            environment {
                VERSION = readMavenPom().getVersion()
            }

            steps {
                sh "VERSION=${VERSION} ./provision/build.sh"
            }
        }
        stage('upload image to registry') {
            agent any

            environment {
                VERSION = readMavenPom().getVersion()
            }

            steps {
                withDockerRegistry([credentialsId: "dockerhubstatfulservice", url: ""]) {
                    sh "VERSION=${VERSION} ./provision/upload.sh"
                }
            }
        }
    }
}
