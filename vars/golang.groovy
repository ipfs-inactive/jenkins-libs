def call() {
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
              withEnv(["GOROOT=${root}", "GOPATH=${originalWs}", "PATH=$PATH;${root}\\bin;${originalWs}\\bin"]) {
                bat 'go get -v github.com/whyrusleeping/gx'
                bat 'go get -v github.com/whyrusleeping/gx-go'
                checkout scm
                bat 'gx --verbose install --global'
                bat 'gx-go rewrite'
                bat "go test -v ./..."
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
              withEnv(["GOROOT=${root}", "GOPATH=${originalWs}", "PATH=$PATH:${root}/bin:${originalWs}/bin"]) {
                sh 'go get -v github.com/whyrusleeping/gx'
                sh 'go get -v github.com/whyrusleeping/gx-go'
                checkout scm
                sh 'gx --verbose install --global'
                sh 'gx-go rewrite'
                sh "go test -v ./..."
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
              withEnv(["GOROOT=${root}", "GOPATH=${originalWs}", "PATH=$PATH:${root}/bin:${originalWs}/bin"]) {
                sh 'go get -v github.com/whyrusleeping/gx'
                sh 'go get -v github.com/whyrusleeping/gx-go'
                checkout scm
                sh 'gx --verbose install --global'
                sh 'gx-go rewrite'
                sh "go test -v ./..."
              }
            }
          }
        }
      }
    )
  }
}

