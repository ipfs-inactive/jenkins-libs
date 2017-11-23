def call() {
 stage('Pre-Tests') {
  echo 'before tests'
 }
 stage('Tests') {
  parallel(
   "Windows": {
    node(label: 'windows') {
     ansiColor('xterm') {
      bat 'git config --global core.autocrlf input'
      checkout scm
      fileExists 'package.json'
      nodejs('9.2.0') {
       bat 'del /s /q node_modules'
       bat 'npm install yarn@1.3.2'
       bat './node_modules/.bin/yarn config set msvs_version 2015 --global'
       bat './node_modules/.bin/yarn --mutex network'
       bat './node_modules/.bin/yarn test'
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
       sh 'rm -rf node_modules/'
       sh 'npm install yarn@1.3.2'
       sh './node_modules/.bin/yarn --mutex network'
       sh './node_modules/.bin/yarn test'
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
       sh 'rm -rf node_modules/'
       sh 'npm install yarn@1.3.2'
       sh './node_modules/.bin/yarn --mutex network'
       wrap([$class: 'Xvfb', parallelBuild: true, autoDisplayName: true]) {
        sh './node_modules/.bin/yarn test'
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
