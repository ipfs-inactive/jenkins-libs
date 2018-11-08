// Resolve takes a website and record, and makes it into a URL that can be
// resolved with `ipfs resolve $DOMAIN`, removing _dnslink if needed
def Resolve(opts = []) {
  assert opts['website'] : 'You need to provide the `website` argument'

  def zone = opts['website']
  def record = opts['record']


  if (record) {
    def full = [record, zone].join('.')
    def reg = ~/^_dnslink./
    return full - reg
  } else {
    return zone
  }
}

def call(opts = []) {
  def hashToPin
  def nodeMultiaddrs
  def websiteHash
  def previousWebsiteHash

  def githubOrg
  def githubRepo
  def gitCommit

  assert opts['website'] : "You need to pass in zone as the `website` argument "
  assert opts['record'] : "You need to pass in name of the record as the `record` argument "

  def website = opts['website']
  // Need to take into account BRANCH_NAME here
  def record = opts['record']
  // def record = opts['record']["$BRANCH_NAME"]
  def buildDirectory = './public'
  def disablePublish = false

  if (record instanceof CharSequence) {
    record = ['master': record]
  }

  if (opts['build_directory']) {
    buildDirectory = opts['build_directory']
  }

  if (opts['disable_publish']) {
    disablePublish = opts['disable_publish']
  }

  // Maximum runtime: 1 hour
  timeout(time: 1, unit: 'HOURS') {
    stage('build website') {
        node(label: 'linux') {
            // Figure out our IP + Multiaddr for master to ensure connection
            nodeMultiaddrs = sh returnStdout: true, script: "ipfs id --format='<addrs>\n'"
            def details = checkout scm

            // Parse Github Org + Repo
            def origin = details.GIT_URL
            def splitted = origin.split("[./]")
            githubOrg = splitted[-3]
            githubRepo = splitted[-2]

            // Get commit from branch OR latest commit in PR
            def isPR = "$BRANCH_NAME".startsWith('PR-')
            if (isPR) {
                gitCommit = sh returnStdout: true, script: "git rev-parse remotes/origin/$BRANCH_NAME"
            } else {
                gitCommit = details.GIT_COMMIT
            }

            // Get the resolvable domain for "ipfs name resolve"
            resolvableDomain = Resolve(opts)

            // Get the current hash of the website
            currentHash = sh(returnStdout: true, script: "ipfs name resolve /ipns/$resolvableDomain || true").trim()

            def containerID
            try {
              // Get the list of previous versions if it exists
              sh "ipfs get $currentHash/_previous-versions > _previous-versions || true"

              // Build Website
              sh 'docker pull ipfs/ci-websites:latest'
              // Create container
              containerID = sh(returnStdout: true, script: "docker create ipfs/ci-websites make -C /site build").trim()
              // Copy site to container
              sh "docker cp \$(pwd)/. $containerID:/site"
              // Run build
              sh "docker start -ai $containerID"
              // Grab finished build
              sh "docker cp $containerID:/site/$buildDirectory ./site"
              // Remove container
              sh "docker rm $containerID"

              // Add the website to IPFS
              currentWebsite = sh(returnStdout: true, script: "ipfs add -rQ ./site").trim()

              // Add the link to the _previous-versions with $currentHash
              versionsHash = sh(returnStdout: true, script: "ipfs add -Q ./_previous-versions").trim()
              websiteHash = sh(returnStdout: true, script: "ipfs object patch $currentWebsite add-link _previous-versions $versionsHash").trim()

              // If previousHash (currently deployed) is same as websiteHash, we can skip
              if (currentHash == '/ipfs/' + websiteHash) {
                  currentBuild.result = hudson.model.Result.SUCCESS.toString()
                  println "This build is already the latest and deployed version"
                  cleanWs()
                  return
              }

              // Now we just have to add the previous link before
              sh "echo $currentHash >> ./_previous-versions"
              versionsHash = sh(returnStdout: true, script: "ipfs add -Q ./_previous-versions").trim()
              websiteHash = sh(returnStdout: true, script: "ipfs object patch $websiteHash add-link _previous-versions $versionsHash").trim()
              sh "ipfs pin add --progress $websiteHash"
              sh "nohup curl --max-time 900 -s \"https://node" + new Random().nextInt(2) +".preload.ipfs.io/api/v0/refs?r=true&arg=${websiteHash}\" >/dev/null 2>&1 &"
              cleanWs()
            } catch (err) {
              currentBuild.result = hudson.model.Result.FAILURE.toString()
              sh "docker rm $containerID || true"
              cleanWs()
              error('Unable to build website. Please check errors above')
            }
        }
    }
    stage('pin + publish preview + publish dns record update') {
      if (currentBuild.result == hudson.model.Result.SUCCESS.toString()) {
        println "This build is already the latest and deployed version"
        return
      }
      node(label: 'master') {
          withEnv(["IPFS_PATH=/home/ubuntu/.ipfs"]) {
            lines = nodeMultiaddrs.readLines()
            lines.each { line ->
              def process = "ipfs swarm connect $line".execute(["IPFS_PATH=/home/ubuntu/.ipfs"], null)
              println "ipfs swarm connect $line"
              def output = new StringWriter(), error = new StringWriter()
              process.waitForProcessOutput(output, error)
              println "exit value=${process.exitValue()}"
              println "OUT: $output"
              println "ERR: $error"
            }
            sh "ipfs refs -r $websiteHash"
            sh "ipfs pin add --progress $websiteHash"
            sh "nohup curl --max-time 900 -s \"https://node" + new Random().nextInt(2) +".preload.ipfs.io/api/v0/refs?r=true&arg=${websiteHash}\" >/dev/null 2>&1 &"
          }
          def websiteUrl = "https://ipfs.io/ipfs/$websiteHash"
          sh "set +x && curl -X POST -H 'Content-Type: application/json' --data '{\"state\": \"success\", \"target_url\": \"$websiteUrl\", \"description\": \"A rendered preview of this commit\", \"context\": \"Rendered Preview\"}' -H \"Authorization: Bearer \$(cat /tmp/userauthtoken)\" https://api.github.com/repos/$githubOrg/$githubRepo/statuses/$gitCommit"
          echo "New website: $websiteUrl"
          if (record["$BRANCH_NAME"] && !disablePublish) {
            sh 'wget --quiet https://ipfs.io/ipfs/QmUFECnqabdoRJePDAQ35awTWeoQhHiz1LujSh3zwBBXCz/dnslink-dnsimple -O dnslink-dnsimple'
            sh 'chmod +x dnslink-dnsimple'
            token = readFile '/tmp/dnsimpletoken'
            token = token.trim()
            withEnv(["DNSIMPLE_TOKEN=$token"]) {
                sh "./dnslink-dnsimple $website /ipfs/$websiteHash ${record["$BRANCH_NAME"]}"
            }
          }
      }
    }
  }
}
