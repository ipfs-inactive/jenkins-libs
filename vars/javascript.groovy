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
def windowsBuildStep (version) {
  node(label: 'windows') { ansiColor('xterm') { withEnv(['CI=true']) {
    // need to make sure we're using the right line endings
    bat 'git config --global core.autocrlf input'
    checkout scm
    fileExists 'package.json'
    nodejs(version) {
      // delete node_modules if it's there
      bat 'del /s /q node_modules >nul 2>&1'
      // install local version of yarn (prevent concurrency issues again)
      bat 'npm install yarn@' + yarnVersion
      // force visual studio version
      bat yarnPath + ' config set msvs_version 2015 --global'
      // install dependencies with a mutex lock
      bat yarnPath + ' --mutex network'
      // run actual tests
      stash(name: 'windows'+version)
    }
  }}}
}

// Step for running tests on a specific nodejs version with unix compatible OS
def unixBuildStep(version, nodeLabel) {
  node(label: nodeLabel) { ansiColor('xterm') { withEnv(['CI=true']) {
    checkout scm
    fileExists 'package.json'
    nodejs(version) {
      sh 'rm -rf node_modules/'
      sh 'npm install yarn@' + yarnVersion
      sh yarnPath + ' --mutex network'
      stash(name: nodeLabel+version)
    }
  }}}
}

def windowsTestStep(cmd, version) {
  node(label: 'windows') { ansiColor('xterm') { withEnv(['CI=true']) {
    unstash(name: 'windows'+version)
    nodejs(version) {
      try {
        bat cmd
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
    unstash(name: nodeLabel+version)
    nodejs(version) {
      try {
        if (nodeLabel == 'linux') { // if it's linux, we need xvfb for display emulation (chrome)
          wrap([$class: 'Xvfb', parallelBuild: true, autoDisplayName: true]) {
            sh cmd
          }
        } else {
          sh cmd
        }
      } catch (err) {
        throw err
      } finally {
        junit allowEmptyResults: false, testResults: 'junit-report-*.xml'
      }
    }
  }}}
}


// Helper function for getting the right platform + version
def getBuildStep(os, version) {
  return {
    if (os == 'macos' || os == 'linux') {
      return unixBuildStep(version, os)
    }
    if (os == 'windows') {
      return windowsBuildStep(version)
    }
  }
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
 stage('Build') {
  // Create map for all the os+version combinations
  def buildSteps = [:]
  for (os in osToTests) {
      for (nodejsVersion in nodejsVersionsToTest) {
          def stepName = os + ' - ' + nodejsVersion
          buildSteps[(stepName)] = getBuildStep(os, nodejsVersion)
      }
  }
  timeout(time: 1, unit: 'HOURS') {
    parallel buildSteps
  }
 }
 stage('Test') {
  // check if test:* exists and add `npm run test:*`
  // if no test:* was found, add `npm run test`
  def testSteps = [:]
  for (os in osToTests) {
      for (nodejsVersion in nodejsVersionsToTest) {
          def stepName = os + ' - ' + nodejsVersion
          if (hasAegirScripts) {
            testSteps[(stepName + " test:node")]    = getTestStep("npm run test:node", os, nodejsVersion)
            testSteps[(stepName + " test:browser")] = getTestStep("npm run test:browser", os, nodejsVersion)
            // testSteps[(stepName)] = getTestStep("npm test:webworker", os, nodejsVersion)
          } else {
            testSteps[(stepName)] = getTestStep("npm run test", os, nodejsVersion)
          }
      }
  }
  timeout(time: 1, unit: 'HOURS') {
    parallel testSteps
  }
 }
}
