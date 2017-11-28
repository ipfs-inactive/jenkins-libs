import groovy.transform.Field
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
def windowsStep () {
  node(label: 'windows') {
    ansiColor('xterm') {
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
        // run actual tests
        bat yarnPath + ' make'
				archiveArtifacts 'out/make/*'
      }
    }
  }
}

// Step for running tests on a specific nodejs version with unix compatible OS
def unixStep(nodeLabel) {
  node(label: nodeLabel) {
    ansiColor('xterm') {
      checkout scm
      fileExists 'package.json'
      nodejs('9.2.0') {
        sh 'rm -rf node_modules/'
        sh 'npm install yarn@' + yarnVersion
        sh yarnPath + ' --mutex network'
				sh yarnPath + ' make'
				archiveArtifacts 'out/make/*'
      }
    }
  }
}

// Helper function for getting the right platform + version
def getStep(os) {
  return {
    if (os == 'macos' || os == 'linux') {
      return unixStep(os)
    }
    if (os == 'windows') {
      return windowsStep()
    }
  }
}


def call() {
 stage('Pre-Tests') {
  echo 'before tests'
 }
 stage('Builds') {
  // Create map for all the os+version combinations
  def steps = [:]
  for (os in osToTests) {
		def stepName = os + ' Build'
		steps[(stepName)] = getStep(os)
  }
  // execute those steps in parallel
  parallel steps
 }
 stage('Post-Tests') {
  echo 'All completed, yay!'
 }
}
