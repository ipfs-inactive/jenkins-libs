def call(body) {
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

	node {
		def nodeHome = tool name: config.version, type: 'jenkins.plugins.nodejs.tools.NodeJSInstallation'
		withEnv(["PATH=${env.PATH}:${nodeHome}/bin"]) {
			stage("Checkout - ${config.version}") {
				checkout scm
			}
			stage("Build - ${config.version}") {
				sh "${nodeHome}/bin/npm install"
			}
			stage("Test - ${config.version}") {
				sh "${nodeHome}/bin/npm run test:node"
			}
		}
	}
}
