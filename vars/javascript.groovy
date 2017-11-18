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
       bat 'npm install --global yarn@1.3.2'
       bat 'yarn'
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
       sh 'yarn'
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
       sh 'yarn'
       wrap([$class: 'Xvfb', parallelBuild: true]) {
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
