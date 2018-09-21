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

// Installs custom node_modules
def installCustomModules (os, modules) {
  if (modules != []) {
    def modulesAsString = modules.collect {
      it.key + "@" + it.value
    }.join(" ")
    run(os, 'npm install ' + modulesAsString)
  }
}

def isWindows (os) {
  return os == 'windows'
}

// Cross-platform sh/bat function
def run(os, cmd) {
  if (isWindows(os)) {
    bat(cmd)
  } else {
    sh(cmd)
  }
}

// Same as `run` but with returnStdout
def runReturnStdout(os, cmd) {
  if (isWindows(os)) {
    return bat(script: cmd, returnStdout: true).trim()
  } else {
    return sh(script: cmd, returnStdout: true).trim()
  }
}

// Same as `run` but with returnStatus
def runReturnStatus(os, cmd) {
  if (isWindows(os)) {
    return bat(script: cmd, returnStatus: true)
  } else {
    return sh(script: cmd, returnStatus: true)
  }
}

// Checks if package.json contains a certain script
def packageHasScript(os, script) {
  def unix = !isWindows(os)
  def catCmd = unix ? 'cat' : 'type'
  def grepCmd = unix ? 'grep' : 'FINDSTR'
  def statusCode = runReturnStatus(os, catCmd + ' package.json | ' + grepCmd + ' ' + script)
  return statusCode == 0
}

// Function to wrap calls that needs to cleanup after themselves
def postClean (f) {
  try {
    f()
  } catch (err) {
    throw err
  } finally {
    cleanWs()
  }
}

// Installs dependencies + custom_modules, can turn off/on scripts
def installDependencies (os, wantedNpmVersion, customModules, ignoreScripts = false) {
  // TODO currently ignored, should be configured on the worker rather than pipeline
  // currentVersion = run('npm --version')
  // if (currentVersion != wantedNpmVersion) {
  //   run('npm install -g npm@' + wantedNpmVersion)
  // }
  if (isWindows(os)) {
    bat 'npm config set msvs_version 2015 --global'
  }
  if (ignoreScripts) {
    run(os, 'npm install --ignore-scripts')
  } else {
    run(os, 'npm install')
  }
  installCustomModules(os, customModules)
}

// Function to wrap calls that might make step unstable rather than failing
// Used to run the tests because if tests fail, should be marked as unstable (tests failing)
// rather than failing (something went seriously wrong)
// TODO doesn't actually set the build to unstable but instead marks it as FAILING
// as a Jenkins bug prevents us from just marking one stage/node as unstable
// https://issues.jenkins-ci.org/browse/JENKINS-39203
def markUnstableIfFail (name, context, f) {
  // should be something like ci/jenkins/windows/11.0.1/test:node
  def ciContext = 'ci/jenkins/' + context
  githubNotify(
    description: name + ' in progress',
    status: 'PENDING',
    context: ciContext
  )
  try {
    f()
    githubNotify(
      description: name + ' passed',
      status: 'SUCCESS',
      context: ciContext
    )
  } catch (err) {
    githubNotify(
      description: name + ' failed',
      status: 'FAILURE',
      context: ciContext
    )
    throw err
  }
  // Uncomment once JENKINS-39203 is fixed
  // try {
  //   f()
  // } catch (err) {
  //   currentBuild.result = 'UNSTABLE'
  //   println err
  // }
}

def collectTestResults (f) {
  try {
    f()
  } catch (err) {
    throw err
  } finally {
    junit allowEmptyResults: true, testResults: 'junit-report-*.xml'
  }
}

// Runs common tests for a platform
def runTests (os, nodejsVersions) {
  def hasNodeTests = false
  def hasBrowserTests = false
  def hasWebWorkerTests = false

  // Need to allocate a node to fetch the stash
  // TODO should refactor this to be able to do without a node
  node(label: 'linux') {
    def data = readVariableStash('variables')
    hasNodeTests = data.hasNodeTests
    hasBrowserTests = data.hasBrowserTests
    hasWebWorkerTests = data.hasWebWorkerTests
  }

  def sourceStash = 'source-' + os
  def depsStash = 'deps-' + os + '-' + nodejsVersions[0]

  def linuxSteps = [:]
  if (hasNodeTests) {
    for (nodejsVersion in nodejsVersions) {
      def version = nodejsVersion
      def stepName = version + ' test:node'
      def depsVersionStash = 'deps-' + os + '-' + version
      def context = os + '/' + version + '/test:node'

      linuxSteps[stepName] = {node(label: os) { postClean {
        unstash sourceStash
        unstash depsVersionStash
        nodejs(version) {
          collectTestResults { markUnstableIfFail 'node tests', context, {
            run(os, 'npm run test:node')
          }}
        }
      }}}
    }
  }
  if (hasBrowserTests) {
   linuxSteps['test:browser'] = {node(label: os) { postClean {
     unstash sourceStash
     unstash depsStash
     nodejs(nodejsVersions[0]) {
       def testCmd = 'npm run test:browser'
       def context = os + '/test:browser'
       if (os == 'linux') {
         wrap([$class: 'Xvfb', parallelBuild: true, autoDisplayName: true]) {
           collectTestResults { markUnstableIfFail 'browser tests', context, {
             run(os, testCmd)
           }}
         }
       } else {
         collectTestResults { markUnstableIfFail 'browser tests', context, {
           run(os, testCmd)
         }}
       }
     }
   }}}
  }
  if (hasWebWorkerTests) {
   linuxSteps['test:webworker'] = {node(label: os) { postClean {
     unstash sourceStash
     unstash depsStash
     nodejs(nodejsVersions[0]) {
       def testCmd = 'npm run test:webworker'
       def context = os + '/test:webworker'
       if (os == 'linux') {
         wrap([$class: 'Xvfb', parallelBuild: true, autoDisplayName: true]) {
           collectTestResults { markUnstableIfFail 'webworker tests', context, {
             run(os, testCmd)
           }}
         }
       } else {
         collectTestResults { markUnstableIfFail 'webworker tests', context, {
           run(os, testCmd)
         }}
       }
     }
   }}}
  }
  parallel linuxSteps
}

// Utility function to use with writeVariableStash
def createInitialJSONData () {
  return readJSON(text: '{}')
}

def writeVariableStash (name, jsonData) {
  writeJSON(file: name + '.jenkins.json', json: jsonData)
  stash(name: 'json-' + name, includes: name + '.jenkins.json')
}

def readVariableStash (name) {
  unstash('json-' + name)
  return readJSON(file: name + '.jenkins.json')
}

def readVariableStash (name, variable) {
  def data = readVariableStash(name)
  return data[variable]
}

def call(opts = []) {
  // Which NodeJS versions to use
  def defaultNodeVersions = [
    '8.11.3',
    '10.4.1'
  ]
  // Which OSes to test on
  def osToTests = [
    'macos',
    'windows',
    'linux'
  ]

  def yarnVersion = '1.9.4'
  def yarnPath = './node_modules/.bin/yarn --registry="https://registry.npmjs.com"'
  def yarnInstallRetries = 3
  def npmVersion = '6.4.1'

   def nodejsVersions = defVal(opts['nodejs_versions'], defaultNodeVersions)
   def customModules = defVal(opts['node_modules'], [])
   // TODO should be automatically infered
   def coverageEnabled = defVal(opts['coverage'], true)
   // TODO linting should be infered as well

   pipeline {
     agent none
     environment {
       CI = true
     }
     options {
       ansiColor('xterm')
       // Enables retrying of failing stages
       preserveStashes(buildCount: 20)
       // TODO only shows up in classic UI, not Blue Ocean :(
       timestamps()
     }
     stages {
       // TODO currently run on each platform due to cross-platform issues, but
       // should be able to run once on linux and shared on platforms
       stage('Fetch Source') {
         steps {
           script {
             def fetchSteps = [:]
             for (os in osToTests) {
              def sourceStash = 'source-' + os
              fetchSteps[os] = {
                node(label: os) { postClean {
                  run(os, 'git config --global core.autocrlf input')
                  checkout scm
                  stash name: sourceStash, excludes: 'node_modules/**', useDefaultExcludes: false
                }}
              }
             }
             parallel(fetchSteps)
           }
         }
       }

       stage('Check available scripts') {
        steps {
          script {
            def os = 'linux'
            node(label: os) { postClean {
              unstash 'source-linux'
              jsonData = createInitialJSONData()
              jsonData.hasNodeTests = packageHasScript(os, 'test:node')
              jsonData.hasBrowserTests = packageHasScript(os, 'test:browser')
              jsonData.hasWebWorkerTests = packageHasScript(os, 'test:webworker')
              writeVariableStash('variables', jsonData)
            }}
          }
        }
       }

       // TODO should be able to run on one platform (linux) and just post-install
       // on each platform + version but npm works differently on platforms...
       stage('Fetch Deps') { steps { script {
         def depsSteps = [:]
         for (os in osToTests) {
           def stepName = os
           def rawDepsStash = 'raw-deps-' + os
           def sourceStash = 'source-' + os
           def version = nodejsVersions[0]
           def currentOS = os

           depsSteps[stepName] = {node(label: currentOS) { postClean {
             unstash sourceStash
             nodejs(version) {
               installDependencies(currentOS, npmVersion, customModules, true)
               stash name: rawDepsStash, includes: 'node_modules/**', useDefaultExcludes: false
             }
           }}}
         }
         parallel depsSteps
       }}}
       stage('Deps Post-Install') { steps { script {
         def depsSteps = [:]
         for (os in osToTests) {
           for (nodejsVersion in nodejsVersions) {
             def stepName = os + ' - ' + nodejsVersion
             def rawDepsStash = 'raw-deps-' + os
             def depsStash = 'deps-' + os + '-' + nodejsVersion
             def sourceStash = 'source-' + os
             def version = nodejsVersion
             def currentOS = os

             depsSteps[stepName] = {node(label: currentOS) { postClean {
               unstash sourceStash
               unstash rawDepsStash
               nodejs(version) {
                 // Might have to add this at one point but seems fine for now
                 // installDependencies(currentOS, npmVersion, customModules)
                 if (isWindows(currentOS)) {
                   bat 'npm config set msvs_version 2015 --global'
                 }
                 run(currentOS, 'npm rebuild')
                 stash name: depsStash, includes: 'node_modules/**', useDefaultExcludes: false
               }
             }}}
           }
         }
         parallel depsSteps
       }}}

       stage('Checks') {
        steps {
          script {
            def os = 'linux'
            def checksSteps = [:]
            checksSteps['codelint'] = { node(label: os) { postClean {
              unstash 'source-linux'
              unstash 'deps-linux-' + nodejsVersions[0]
              nodejs(nodejsVersions[0]) {
                markUnstableIfFail 'code linting', 'codelint', {
                  run(os, 'npm run lint')
                }
              }
            }}}
            checksSteps['commitlint'] = { node(label: os) { postClean {
              unstash 'source-linux'
              nodejs(nodejsVersions[0]) {
                sh 'npm install --no-lockfile @commitlint/config-conventional @commitlint/cli'
                def commit = runReturnStdout(os, "git rev-parse remotes/origin/$BRANCH_NAME")
                run(os, 'git remote set-branches origin master && git fetch')
                markUnstableIfFail 'commit linting', 'commitlint', {
                  run(os, "./node_modules/.bin/commitlint --extends=@commitlint/config-conventional --from=remotes/origin/master --to=$commit")
                }
              }
            }}}
            parallel checksSteps
          }
        }
       }

       stage('Linux Tests') {
        steps {
          script {
            runTests('linux', nodejsVersions)
          }
        }
       }
       stage('Windows Tests') {
        steps {
          script {
            runTests('windows', nodejsVersions)
          }
        }
       }
       stage('macOS Tests') {
        steps {
          script {
            runTests('macos', nodejsVersions)
          }
        }
       }
       stage('Coverage') {
        steps {
          script {
            def os = 'linux'
            node(label: os) { postClean {
              unstash 'source-linux'
              unstash 'deps-linux-' + nodejsVersions[0]
              nodejs(nodejsVersions[0]) {
                def repo = runReturnStdout(os, "git remote get-url origin | cut -d '/' -f 4,5 | cut -d '.' -f 1")
                withCredentials([
                  string(
                    credentialsId: 'codecov-test-access-code',
                    variable: 'CODECOV_ACCESS_TOKEN'
                  )]) {
                  def codecovToken = runReturnStdout(os, "curl --silent \"https://codecov.io/api/gh/$repo?access_token=\$CODECOV_ACCESS_TOKEN\" | node -e \"let data = '';process.stdin.on('data', (d) => data = data + d.toString());process.stdin.on('end', () => console.log(JSON.parse(data).repo.upload_token));\"")
                  withEnv(["CODECOV_TOKEN=$codecovToken"]) {
                 markUnstableIfFail 'coverage', 'coverage', {
                   run(os, 'npm run coverage -u -p codecov')
                 }
                  }
                }
              }
          }}}}
       }
     }
   }
}

