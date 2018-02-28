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

def windowsTestStep(cmd, version) {
  node(label: 'windows') { ansiColor('xterm') { withEnv(['CI=true']) {
    bat 'git config --global core.autocrlf input'
    checkout scm
    fileExists 'package.json'
    nodejs(version) {
      try {
        bat 'del /s /q node_modules >nul 2>&1'
        // install local version of yarn (prevent concurrency issues again)
        bat 'npm install yarn@' + yarnVersion
        // force visual studio version
        bat yarnPath + ' config set msvs_version 2015 --global'
        // install dependencies with a mutex lock
        bat yarnPath + ' --mutex network'
        bat yarnPath + ' ' + cmd
      } catch (err) {
        throw err
      } finally {
        junit allowEmptyResults: false, testResults: 'junit-report-*.xml'
      }
    }
  }}}
}

def unixTestStep(cmd, version, nodeLabel) {
  node(label: nodeLabel) { ansiColor('xterm') { withEnv(['CI=true']) {
    checkout scm
    fileExists 'package.json'
    nodejs(version) {
      try {
        sh 'rm -rf node_modules/'
        sh 'npm install yarn@' + yarnVersion
        sh yarnPath + ' --mutex network'
        if (nodeLabel == 'linux') { // if it's linux, we need xvfb for display emulation (chrome)
          wrap([$class: 'Xvfb', parallelBuild: true, autoDisplayName: true]) {
            sh yarnPath + ' ' + cmd
          }
        } else {
          sh yarnPath + ' ' + cmd
        }
      } catch (err) {
        throw err
      } finally {
        junit allowEmptyResults: false, testResults: 'junit-report-*.xml'
      }
    }
  }}}
}

def getTestStep(cmd, os, version) {
  return {
    if (os == 'macos' || os == 'linux') {
      return unixTestStep(cmd, version, os)
    }
    if (os == 'windows') {
      return windowsTestStep(cmd, version)
    }
  }
}


def call() {
 def hasAegirScripts = true
 stage('Test') {
  def testSteps = [:]
  for (os in osToTests) {
      for (nodejsVersion in nodejsVersionsToTest) {
          def stepName = os + ' - ' + nodejsVersion
          if (hasAegirScripts) {
            testSteps[(stepName + " test:node")]      = getTestStep("test:node", os, nodejsVersion)
            testSteps[(stepName + " test:browser")]   = getTestStep("test:browser", os, nodejsVersion)
            testSteps[(stepName + " test:webworker")] = getTestStep("test:webworker", os, nodejsVersion)
          } else {
            testSteps[(stepName)] = getTestStep("test", os, nodejsVersion)
          }
      }
  }
  timeout(time: 1, unit: 'HOURS') {
    parallel testSteps
  }
 }
}
