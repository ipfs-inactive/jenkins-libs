def call() {
  node(label: 'linux') {
    ansiColor('xterm') {
      env.GO_HOME = "${tool name: '1.9.2', type: 'go'}"
      env.PATH="${env.GO_HOME}/bin:${env.PATH}"
      env.PATH="${env.HOME}/go/bin:${env.PATH}"
      stage("gx") {
        sh 'go get -v github.com/whyrusleeping/gx'
				sh 'go get -v github.com/whyrusleeping/gx-go'
      }
      stage("checkout") {
        checkout scm
      }
      stage("deps") {
        sh 'gx --verbose install --global'
        sh 'gx-go rewrite'
      }
      stage('tests') {
        sh "go test -v ./..."
      }
    }
  }
}

