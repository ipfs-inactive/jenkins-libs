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
       bat 'npm install --global yarn@1.3.2'
       bat 'yarn config set msvs_version 2015 --global'
       bat 'yarn --mutex network'
       bat 'yarn test'
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
       sh 'npm install --global yarn@1.3.2'
       sh 'yarn --mutex network'
       sh 'yarn test'
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
       sh 'npm install --global yarn@1.3.2'
       sh 'yarn --mutex network'
       wrap([$class: 'Xvfb']) {
        sh 'yarn test'
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
