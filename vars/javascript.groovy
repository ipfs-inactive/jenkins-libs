import groovy.transform.Field
// Which NodeJS versions to test on
@Field final List defaultNodeVersions = [
  '8.11.1',
  '9.2.0'
]
// Which OSes to test on
@Field final List osToTests = [
  'macos',
  'windows',
  'linux'
]
// Global for which yarn version to use
@Field final String yarnVersion = '1.5.1'
// Global for having the path to yarn (prevent concurrency issue with yarn cache)
@Field final String yarnPath = './node_modules/.bin/yarn'

// Step for running tests on a specific nodejs version with windows
def windowsStep (version) {
  node(label: 'windows') { ansiColor('xterm') { withEnv(['CI=true']) {
    def ciContext = 'continuous-integration/jenkins/windows/' + version
    githubNotify description: 'Tests in progress',  status: 'PENDING', context: ciContext
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
      try {
        bat yarnPath + ' test'
        githubNotify description: 'Tests passed',  status: 'SUCCESS', context: ciContext
      } catch (err) {
        githubNotify description: 'Tests failed',  status: 'FAILURE', context: ciContext
        throw err
      } finally {
        junit allowEmptyResults: true, testResults: 'junit-report-*.xml'
        cleanWs()
      }
    }
  }}}
}

// Step for running tests on a specific nodejs version with unix compatible OS
def unixStep(version, nodeLabel) {
  node(label: nodeLabel) { ansiColor('xterm') { withEnv(['CI=true']) {
    def ciContext = 'continuous-integration/jenkins/' + nodeLabel + '/' + version
    githubNotify description: 'Tests in progress',  status: 'PENDING', context: ciContext
    checkout scm
    fileExists 'package.json'
    nodejs(version) {
      sh 'rm -rf node_modules/'
      sh 'npm install yarn@' + yarnVersion
      sh yarnPath + ' --mutex network'
      try {
        if (nodeLabel == 'linux') { // if it's linux, we need xvfb for display emulation (chrome)
          wrap([$class: 'Xvfb', parallelBuild: true, autoDisplayName: true]) {
            sh yarnPath + ' test'
            githubNotify description: 'Tests passed',  status: 'SUCCESS', context: ciContext
          }
        } else {
          sh yarnPath + ' test'
          githubNotify description: 'Tests passed',  status: 'SUCCESS', context: ciContext
        }
      } catch (err) {
        githubNotify description: 'Tests failed',  status: 'FAILURE', context: ciContext
        throw err
      } finally {
        junit allowEmptyResults: true, testResults: 'junit-report-*.xml'
        cleanWs()
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


// Helper to receive the value if it's not empty, or return a default value
def defVal (value, defaultValue) {
  if (value == null || value == []) {
    return defaultValue
  } else {
    if (value instanceof java.util.LinkedHashMap) {
      return value + defaultValue
    } else {
      return value
    }
  }
}


def call(opts = []) {
 def nodejsVersions = defVal(opts['nodejs_versions'], defaultNodeVersions)
 stage('Tests') {
  // Create map for all the os+version combinations
  def steps = [:]
  for (os in osToTests) {
      for (nodejsVersion in nodejsVersions) {
          def stepName = os + ' - ' + nodejsVersion
          steps[(stepName)] = getStep(os, nodejsVersion)
      }
  }
  steps['codelint'] = {node(label: 'linux') { ansiColor('xterm') { withEnv(['CI=true']) {
    def ciContext = 'ci/jenkins/codelint'
    githubNotify description: 'Linting in progress',  status: 'PENDING', context: ciContext
    checkout scm
    fileExists 'package.json'
    nodejs('9.2.0') {
        sh 'rm -rf node_modules/'
        sh 'npm install yarn@' + yarnVersion
        sh yarnPath + ' --mutex network'
        try {
          sh yarnPath + ' lint'
          githubNotify description: 'Linting passed',  status: 'SUCCESS', context: ciContext
        } catch (err) {
          githubNotify description: 'Linting failed',  status: 'FAILURE', context: ciContext
          throw err
        }
    }
  }}}}
  steps['commitlint'] = {node(label: 'linux') { ansiColor('xterm') { withEnv(['CI=true']) {
    def ciContext = 'ci/jenkins/commitlint'
    githubNotify description: 'Linting in progress',  status: 'PENDING', context: ciContext
    checkout scm
    fileExists 'package.json'
    nodejs('9.2.0') {
        sh 'rm -rf node_modules/'
        sh 'npm install yarn@' + yarnVersion
        sh yarnPath + ' add @commitlint/config-conventional @commitlint/cli'
        try {
          def commit = sh(returnStdout: true, script: "git rev-parse remotes/origin/$BRANCH_NAME").trim()
          sh 'git remote set-branches origin master && git fetch'
          sh "./node_modules/.bin/commitlint --extends=@commitlint/config-conventional --from=remotes/origin/master --to=$commit"
          githubNotify description: 'Linting passed',  status: 'SUCCESS', context: ciContext
        } catch (err) {
          githubNotify description: 'Linting failed',  status: 'FAILURE', context: ciContext
          throw err
        }
    }
  }}}}
  // Maximum runtime: 1 hour
  timeout(time: 1, unit: 'HOURS') {
    // execute those steps in parallel
    parallel steps
  }
 }
}

