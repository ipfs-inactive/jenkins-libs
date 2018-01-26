def call(opts = []) {
  def hashToPin
  def nodeIP
  def nodeMultiaddr
  def websiteHash

  assert opts['website'] : "You need to pass in Website as a argument"
  assert opts['record'] : "You need to pass in Website as a argument"

  def website = opts['website']
  def record = opts['record']

  stage('build website') {
      node(label: 'linux') {
          nodeIP = sh returnStdout: true, script: 'dig +short myip.opendns.com @resolver1.opendns.com'
          echo "$nodeIP"
          nodeMultiaddr = sh returnStdout: true, script: "ipfs id --format='<addrs>\n' | grep $nodeIP"
          echo "$nodeMultiaddr"
          checkout scm
          sh 'docker run -i -v `pwd`:/site ipfs/ci-websites make -C /site build'
          websiteHash = sh returnStdout: true, script: 'ipfs add -rq public | tail -n1'
      }
  }

  stage('connect to worker') {
      node(label: 'master') {
          token = readFile '/tmp/dnsimpletoken'
          // TODO fix newline issue after
          // token = token.trim()
          withEnv(["IPFS_PATH=/efs/.ipfs", "DNSIMPLE_TOKEN=$token"]) {
              sh "ipfs swarm connect $nodeMultiaddr"
              sh "ipfs pin add --progress $websiteHash"
              echo "New website: https://ipfs.io/ipfs/$websiteHash"
              if ("$BRANCH_NAME" == "master") {
                sh 'wget https://ipfs.io/ipfs/QmbSqRiAvC1t14Ysexu7cdmjKrPCkSoDGFHC97nwkvZqu6/dnslink-dnsimple -O dnslink-dnsimple'
                sh 'chmod +x dnslink-dnsimple'
                sh "./dnslink-dnsimple $website /ipfs/$websiteHash $record"
              }
          }
      }
  }
}
