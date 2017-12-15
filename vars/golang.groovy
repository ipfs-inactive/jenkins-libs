import groovy.transform.Field

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

@Field def defaultEnv = ["CI=true"]
@Field def defaultTest = "go test ./..."

def call(opts) {
  def env = defVal(opts.env, defaultEnv)
  def test = defVal(opts.test, defaultTest)
  stage('tests') {
    parallel(
      windows: {
        node(label: 'windows') {
          ansiColor('xterm') {
            def root = tool name: '1.9.2', type: 'go'
            def jobNameArr = "${JOB_NAME}"
            def jobName = jobNameArr.split("/")[0..1].join("\\\\").toLowerCase()
            def originalWs = "${WORKSPACE}"
            ws("${originalWs}\\src\\github.com\\${jobName}") {
              def goEnv = ["GOROOT=${root}", "GOPATH=${originalWs}", "PATH=$PATH;${root}\\bin;${originalWs}\\bin"]
              withEnv(goEnv + env) {
                bat 'go get -v github.com/whyrusleeping/gx'
                bat 'go get -v github.com/whyrusleeping/gx-go'
                checkout scm
                bat 'gx --verbose install --global'
                bat 'gx-go rewrite'
                bat test
              }
            }
          }
        }
      },
      linux: {
        node(label: 'linux') {
          ansiColor('xterm') {
            def root = tool name: '1.9.2', type: 'go'
            def jobNameArr = "${JOB_NAME}"
            def jobName = jobNameArr.split("/")[0..1].join("/").toLowerCase()
            def originalWs = "${WORKSPACE}"
            ws("${originalWs}/src/github.com/${jobName}") {
              def goEnv = ["GOROOT=${root}", "GOPATH=${originalWs}", "PATH=$PATH:${root}/bin:${originalWs}/bin"]
              withEnv(goEnv + env) {
                sh 'go get -v github.com/whyrusleeping/gx'
                sh 'go get -v github.com/whyrusleeping/gx-go'
                checkout scm
                sh 'gx --verbose install --global'
                sh 'gx-go rewrite'
                sh test
              }
            }
          }
        }
      },
      macOS: {
        node(label: 'macos') {
          ansiColor('xterm') {
            def root = tool name: '1.9.2', type: 'go'
            def jobNameArr = "${JOB_NAME}"
            def jobName = jobNameArr.split("/")[0..1].join("/").toLowerCase()
            def originalWs = "${WORKSPACE}"
            ws("${originalWs}/src/github.com/${jobName}") {
              def goEnv = ["GOROOT=${root}", "GOPATH=${originalWs}", "PATH=$PATH:${root}/bin:${originalWs}/bin"]
              withEnv(goEnv + env) {
                sh 'go get -v github.com/whyrusleeping/gx'
                sh 'go get -v github.com/whyrusleeping/gx-go'
                checkout scm
                sh 'gx --verbose install --global'
                sh 'gx-go rewrite'
                sh test
              }
            }
          }
        }
      }
    )
  }
}


