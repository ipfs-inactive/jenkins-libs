def createBadge(Map opts) {
	def title = opts.title
	def text = opts.text
	def color = opts.color
	def fileName = title + '.svg'
	def shieldsFilename = title + '-' + text + '-' + color + '.svg'
	sh("wget 'https://img.shields.io/badge/"+shieldsFilename+"' -O " + fileName)
}

def createFailingBadge(Map opts) {
	opts.color = 'red'
	opts.text = 'failing'
	createBadge(opts)
}

def createPassingBadge(Map opts) {
	opts.color = 'green'
	opts.text = 'passing'
	createBadge(opts)
}

pipeline {
	agent {label 'linux'}
	stages {
		stage('Test') {
			steps {
				createPassingBadge(title: 'node:test')
				archiveArtifacts '*.svg'
			}
		}
	}
}
