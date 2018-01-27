def call(opts = []) {
  def hashToPin
  def nodeIP
  def nodeMultiaddr
  def websiteHash

  def githubOrg
  def githubRepo
  def gitCommit

  assert opts['website'] : "You need to pass in zone as the `website` argument "
  assert opts['record'] : "You need to pass in name of the record as the `record` argument "

  def website = opts['website']
  def record = opts['record']
  def buildDirectory = 'public/'
  if (opts['build_directory']) {
    buildDirectory = opts['build_directory']
  }

  stage('build website') {
      node(label: 'linux') {
          nodeIP = sh returnStdout: true, script: 'dig +short myip.opendns.com @resolver1.opendns.com'
          echo "$nodeIP"
          nodeMultiaddr = sh returnStdout: true, script: "ipfs id --format='<addrs>\n' | grep $nodeIP"
          echo "$nodeMultiaddr"
          def details = checkout scm
          def origin = details.GIT_URL
          def splitted = origin.split("[./]")
          githubOrg = splitted[-3]
          githubRepo = splitted[-2]
          gitCommit = details.GIT_COMMIT
          sh 'docker run -i -v `pwd`:/site ipfs/ci-websites make -C /site build'
          websiteHash = sh returnStdout: true, script: "ipfs add -rQ $buildDirectory"
          websiteHash = websiteHash.trim()
      }
  }

  stage('connect to worker') {
      node(label: 'master') {
          withEnv(["IPFS_PATH=/efs/.ipfs"]) {
              sh "ipfs swarm connect $nodeMultiaddr"
              sh "ipfs pin add --progress $websiteHash"
          }
          def websiteUrl = "https://ipfs.io/ipfs/$websiteHash"
          echo "New website: $websiteUrl"
          sh "set +x && curl -X POST -H 'Content-Type: application/json' --data '{\"state\": \"success\", \"target_url\": \"$websiteUrl\", \"description\": \"A rendered preview of this commit\", \"context\": \"Rendered Preview\"}' -H \"Authorization: Bearer \$(cat /tmp/userauthtoken)\" https://api.github.com/repos/$githubOrg/$githubRepo/statuses/$gitCommit"
          if ("$BRANCH_NAME" == "master") {
            sh 'wget https://ipfs.io/ipfs/QmRhdziJEm7ZaLBB3H7XGcKF8FJW6QpAqGmyB2is4QVN4L/dnslink-dnsimple -O dnslink-dnsimple'
            sh 'chmod +x dnslink-dnsimple'
            token = readFile '/tmp/dnsimpletoken'
            token = token.trim()
            withEnv(["DNSIMPLE_TOKEN=$token"]) {
                sh "./dnslink-dnsimple $website /ipfs/$websiteHash $record"
            }
          }
      }
  }
}

