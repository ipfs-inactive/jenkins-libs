def call(body) {
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

	node {
		def goHome = tool name: 'Go174', type: 'org.jenkinsci.plugins.golang.GolangInstallation'
		withEnv(["PATH=${env.PATH}:${goHome}/bin"]) {
			stage("Checkout") {
				checkout scm
			}
			stage("Build") {
				sh "echo ${goHome}"
				sh "${goHome}/bin/go get ./..."
			}
			stage("Test") {
				sh "${goHome}/bin/go test ./..."
			}
		}
	}
}
