import groovy.transform.Field
// Which NodeJS versions to test on
@Field final List nodejsVersionsToTest = [
  '8.9.1',
  '9.2.0'
]
// Which OSes to test on
@Field final List osToTests = [
  'macos',
  'windows',
  'linux'
]
// Global for which yarn version to use
@Field final String yarnVersion = '1.3.2'
// Global for having the path to yarn (prevent concurrency issue with yarn cache)
@Field final String yarnPath = './node_modules/.bin/yarn'

// Step for running tests on a specific nodejs version with windows
def windowsStep (version) {
  node(label: 'windows') { ansiColor('xterm') { withEnv(['CI=true']) {
    // need to make sure we're using the right line endings
    bat 'git config --global core.autocrlf input'
    checkout scm
    fileExists 'package.json'
    nodejs('9.2.0') {
      // delete node_modules if it's there
      bat 'del /s /q node_modules >nul 2>&1'
      // install local version of yarn (prevent concurrency issues again)
      bat 'npm install yarn@' + yarnVersion
      // force visual studio version
      bat yarnPath + ' config set msvs_version 2015 --global'
      // install dependencies with a mutex lock
      bat yarnPath + ' --mutex network'
      // bat yarnPath + ' add https://github.com/ipfs/aegir.git#add-junit-reports'
      // run actual tests
      try {
        bat yarnPath + ' test'
      } catch (err) {
        throw err
      } finally {
        junit 'junit-report-*.xml' 
      }
    }
  }}}
}

// Step for running tests on a specific nodejs version with unix compatible OS
def unixStep(version, nodeLabel) {
  node(label: nodeLabel) { ansiColor('xterm') { withEnv(['CI=true']) {
    checkout scm
    fileExists 'package.json'
    nodejs(version) {
      sh 'rm -rf node_modules/'
      sh 'npm install yarn@' + yarnVersion
      sh yarnPath + ' --mutex network'
      // sh yarnPath + ' add https://github.com/ipfs/aegir.git#add-junit-reports'
      try {
        if (nodeLabel == 'linux') { // if it's linux, we need xvfb for display emulation (chrome)
          wrap([$class: 'Xvfb', parallelBuild: true, autoDisplayName: true]) {
            sh yarnPath + ' test'
          }
        } else {
          sh yarnPath + ' test'
        }
      } catch (err) {
        throw err
      } finally {
        junit 'junit-report-*.xml' 
      }
    }
  }}}
}

// Helper function for getting the right platform + version
def getStep(os, version) {
  return {
    if (os == 'macos' || os == 'linux') {
      return unixStep(version, os)
    }
    if (os == 'windows') {
      return windowsStep(version)
    }
  }
}


def call() {
 stage('Pre-Tests') {
  echo 'before tests'
 }
 stage('Tests') {
  // Create map for all the os+version combinations
  def steps = [:]
  for (os in osToTests) {
      for (nodejsVersion in nodejsVersionsToTest) {
          def stepName = os + ' - ' + nodejsVersion
          steps[(stepName)] = getStep(os, nodejsVersion)
      }
  }
  // execute those steps in parallel
  parallel steps
 }
 stage('Post-Tests') {
  echo 'All completed, yay!'
 }
}
