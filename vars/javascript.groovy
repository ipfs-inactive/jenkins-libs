import groovy.transform.Field
// Which NodeJS versions to test on
@Field final List defaultNodeVersions = [
  '8.11.3',
  '10.4.1'
]
// Which OSes to test on
@Field final List osToTests = [
  'macos',
  'windows',
  'linux'
]
// Global for which yarn version to use
@Field final String yarnVersion = '1.7.0'
// Global for having the path to yarn (prevent concurrency issue with yarn cache)
@Field final String yarnPath = './node_modules/.bin/yarn --registry="https://registry.npmjs.com"'

// Step for running tests on a specific nodejs version with windows
def windowsStep (version, customModules, buildStep) {
  node(label: 'windows') { ansiColor('xterm') { withEnv(['CI=true']) {
    def ciContext = 'ci/jenkins/windows/' + version + '/' + buildStep
    githubNotify description: 'Tests in progress',  status: 'PENDING', context: ciContext
    // need to make sure we're using the right line endings
    bat 'git config --global core.autocrlf input'
    checkout scm
    fileExists 'package.json'
    nodejs(version) {
      // delete node_modules if it's there
      bat 'if exist node_modules Cmd /C "rmdir /S /Q node_modules"'
      // install local version of yarn (prevent concurrency issues again)
      bat 'npm install yarn@' + yarnVersion
      // force visual studio version
      bat yarnPath + ' config set msvs_version 2015 --global'
      // install dependencies with a mutex lock
      bat yarnPath + ' --mutex network --no-lockfile'
      // Install custom modules if any
      installCustomModules(customModules, true)
      // run actual tests
      try {
        bat yarnPath + ' ' + buildStep
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
def unixStep(version, nodeLabel, customModules, buildStep) {
  node(label: nodeLabel) { ansiColor('xterm') { withEnv(['CI=true']) {
    def ciContext = 'ci/jenkins/' + nodeLabel + '/' + version + '/' + buildStep
    githubNotify description: 'Tests in progress',  status: 'PENDING', context: ciContext
    checkout scm
    fileExists 'package.json'
    nodejs(version) {
      sh 'rm -rf node_modules/'
      sh 'npm install yarn@' + yarnVersion
      sh yarnPath + ' --mutex network --no-lockfile'
      installCustomModules(customModules, false)
      try {
        if (nodeLabel == 'linux') { // if it's linux, we need xvfb for display emulation (chrome)
          wrap([$class: 'Xvfb', parallelBuild: true, autoDisplayName: true]) {
            sh yarnPath + ' ' + buildStep
            githubNotify description: 'Tests passed',  status: 'SUCCESS', context: ciContext
          }
        } else {
          sh yarnPath + ' ' + buildStep
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
def getStep(os, version, customModules, buildStep) {
  return {
    if (os == 'macos' || os == 'linux') {
      return unixStep(version, os, customModules, buildStep)
    }
    if (os == 'windows') {
      return windowsStep(version, customModules, buildStep)
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

def installCustomModules (modules, isWindows) {
  if (modules != []) {
    def modulesAsString = modules.collect {
      it.key + "@" + it.value
    }.join(" ")
    if (isWindows) {
      bat yarnPath + ' add ' + modulesAsString
    } else {
      sh yarnPath + ' add ' + modulesAsString
    }
  }
}

def call(opts = []) {
 def nodejsVersions = defVal(opts['nodejs_versions'], defaultNodeVersions)
 def customModules = defVal(opts['node_modules'], [])
 // TODO right now, each customBuildStep will be run in parallel with each other
 // we should enable the use-case where we want them to run after each other
 def customBuildSteps = defVal(opts['custom_build_steps'], ['test'])
 def coverageEnabled = defVal(opts['coverage'], true)

 stage('Tests') {
  // Create map for all the os+version combinations
  def steps = [:]
  for (os in osToTests) {
      for (nodejsVersion in nodejsVersions) {
          for (buildStep in customBuildSteps) {
            def stepName = os + ' - ' + nodejsVersion + ' - ' + buildStep
            steps[(stepName)] = getStep(os, nodejsVersion, customModules, buildStep)
          }
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
        sh yarnPath + ' --mutex network --no-lockfile'
        installCustomModules(customModules, false)
        try {
          sh yarnPath + ' lint'
          githubNotify description: 'Linting passed',  status: 'SUCCESS', context: ciContext
        } catch (err) {
          githubNotify description: 'Linting failed',  status: 'FAILURE', context: ciContext
          throw err
        } finally {
          cleanWs()
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
        sh yarnPath + ' add --no-lockfile @commitlint/config-conventional @commitlint/cli'
        try {
          def commit = sh(returnStdout: true, script: "git rev-parse remotes/origin/$BRANCH_NAME").trim()
          sh 'git remote set-branches origin master && git fetch'
          sh "./node_modules/.bin/commitlint --extends=@commitlint/config-conventional --from=remotes/origin/master --to=$commit"
          githubNotify description: 'Linting passed',  status: 'SUCCESS', context: ciContext
        } catch (err) {
          githubNotify description: 'Linting failed',  status: 'FAILURE', context: ciContext
          throw err
        } finally {
          cleanWs()
        }
    }
  }}}}
  if (coverageEnabled) {
    steps['coverage'] = {node(label: 'linux') { ansiColor('xterm') { withEnv(['CI=true']) {
      checkout scm
      fileExists 'package.json'
      nodejs('9.2.0') {
        sh 'npm install yarn@' + yarnVersion
        sh yarnPath + ' --mutex network'
        try {
          sh 'env | sort'
          def repo = sh(returnStdout: true, script: "git remote get-url origin | cut -d '/' -f 4,5 | cut -d '.' -f 1").trim()
          withCredentials([string(credentialsId: 'codecov-test-access-code', variable: 'CODECOV_ACCESS_TOKEN')]) {
            def codecovToken = sh(returnStdout: true, script: "curl --silent \"https://codecov.io/api/gh/$repo?access_token=\$CODECOV_ACCESS_TOKEN\" | node -e \"let data = '';process.stdin.on('data', (d) => data = data + d.toString());process.stdin.on('end', () => console.log(JSON.parse(data).repo.upload_token));\"").trim()
            withEnv(["CODECOV_TOKEN=$codecovToken"]) {
              sh yarnPath + ' coverage -u -p codecov'
            }
          }
        } catch (err) {
          println err
        } finally {
          cleanWs()
        }
      }
    }}}}
  }

  //Apply timeout per step
  steps = steps.collect { e ->
    // wrap in additional lambda
    { it ->
      timeout(time: 1, unit: 'HOURS') {
  	    e.value()
      }
	}
  }

  parallel steps
 }
}

