def call() {
 stage('Pre-Tests') {
  echo 'before tests'
 }
 stage('Tests') {
  parallel(
   "Windows": {
    node(label: 'windows') {
     ansiColor('xterm') {
      checkout scm
      fileExists 'package.json'
      nodejs('9.2.0') {
       bat 'npm config set msvs_version 2015 --global'
       bat 'npm config set unsafe-perm=true --global'
       bat 'npm install --verbose'
       bat 'npm test'
      }
     }
    }
   },
   "macOS": {
    node(label: 'macos') {
     ansiColor('xterm') {
      checkout scm
      fileExists 'package.json'
      nodejs('9.2.0') {
       sh 'npm install --verbose'
       sh 'npm test'
      }
     }
    }
   },
   "Linux": {
    node(label: 'linux') {
     ansiColor('xterm') {
      checkout scm
      fileExists 'package.json'
      nodejs('9.2.0') {
       sh 'npm install --verbose'
       wrap([$class: 'Xvfb', parallelBuild: true]) {
        sh 'npm test'
       }
      }
     }
    }
   }
  )
 }
 stage('Post-Tests') {
  echo 'All completed, yay!'
 }
}
