def call() {
  stage('tests') {
    parallel(
      linux: {
        node(label: 'linux') {
          ansiColor('xterm') {
            def root = tool name: '1.9.2', type: 'go'
            def jobNameArr = "${JOB_NAME}"
            def jobName = jobNameArr.split("/")[0..1].join("/")
            def originalWs = "${WORKSPACE}"
            ws("${originalWs}/src/github.com/${jobName}") {
              withEnv(["GOROOT=${root}", "GOPATH=${originalWs}/", "PATH+GO=${root}/bin"]) {
                env.PATH="${GOPATH}/bin:$PATH"
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
            def jobName = jobNameArr.split("/")[0..1].join("/")
            def originalWs = "${WORKSPACE}"
            ws("${originalWs}/src/github.com/${jobName}") {
              withEnv(["GOROOT=${root}", "GOPATH=${originalWs}/", "PATH+GO=${root}/bin"]) {
                env.PATH="${GOPATH}/bin:$PATH"
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

