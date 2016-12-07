def call(body) {
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

	node {
		def nodeHome = tool name: 'NodeJS-6', type: 'jenkins.plugins.nodejs.tools.NodeJSInstallation'
		withEnv(["PATH=${env.PATH}:${nodeHome}/bin"]) {
			stage('Checkout') {
				checkout scm
			}
			stage('Build') {
				sh "env | sort"
				sh "${nodeHome}/bin/npm install"
			}
			stage('Test') {
				sh "${nodeHome}/bin/npm run test:node"
			}
		}
	}
}
