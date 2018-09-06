def call() {
    timeout(time: 1, unit: 'HOURS') {
        stage('tx push -s') {
            node(label: 'linux') {
            ansiColor('xterm') {
                // IF build is for master branch (eg. after PR merge)
                if ( "$BRANCH_NAME" == "master" ) {
                    def gitInfo = checkout scm
                    // AND only if project includes Transifex config file
                    if ( "$gitInfo.GIT_BRANCH" == "master" && fileExists('.tx/config') ) {
                        // THEN run locale sync in ephemeral container using API token from Jenkins env
                        sh "docker run --rm -it -e TX_TOKEN=\"\$TX_TOKEN\" -v \$(pwd):/project lidel/ci-transifex tx push -s --root /project"
                    } else {
                        echo 'Transifex sync was skipped: .tx/config is missing'
                        currentBuild.result = "UNSTABLE"
                    }
                }
            }
            }
        }
    }
}
