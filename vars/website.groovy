def hashToPin
def nodeIP
def nodeMultiaddr
def websiteHash
stage('build website') {
    node(label: 'linux') {
        nodeIP = sh returnStdout: true, script: 'dig +short myip.opendns.com @resolver1.opendns.com'
        echo "$nodeIP"
        nodeMultiaddr = sh returnStdout: true, script: "ipfs id --format='<addrs>\n' | grep $nodeIP"
        echo "$nodeMultiaddr"
        git 'https://github.com/ipfs/website.git'
        sh 'wget https://github.com/gohugoio/hugo/releases/download/v0.34/hugo_0.34_Linux-64bit.deb'
        sh 'sudo dpkg -i hugo_0.34_Linux-64bit.deb'
        nodejs('9.2.0') {
            sh 'make'
        }
        websiteHash = sh returnStdout: true, script: 'ipfs add -rq public | tail -n1'
    }
}

stage('connect to worker') {
    node(label: 'master') {
        withEnv(["IPFS_PATH=/efs/.ipfs"]) {
            sh "ipfs swarm connect $nodeMultiaddr"
            sh "ipfs pin add --progress $websiteHash"
            echo "New website: https://ipfs.io/ipfs/$websiteHash"
        }
    }
}
