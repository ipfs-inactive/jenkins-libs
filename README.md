# Jenkins Libraries

This repository is being used for setting up default/standard pipelines for projects in https://github.com/ipfs/jenkins

# API

A script that sits in `ci/Jenkinsfile` can access these library functions by calling the name of the file and passing arguments.

- [#electron-forge](Electron Forge)
- [#golang](Golang)
- [#javascript](JavaScript)
- [#website-preview-and-deployment-hugo-only](Website)

## Electron Forge

Builds an Electron application by using [Electron Forge](https://electronforge.io/), saving the built artifacts for downloading directly from Jenkins. Creates following binaries : `.dmg`, `.rpm`, `.deb`, `.nupkg` and `.exe` for Debian and other Linux systems, macOS and Windows.

### Example

```groovy
electronForge()
```

## Golang

Runs `go test` and optionally sharness tests for a Go project on Ubuntu, macOS and Windows.

### Example

```groovy
golang([
  env: [TEST_NO_FUSE: true], // optional extra environment variables. Already sets CI=true
  test: 'go test -v ./...' // optional command to run for running tests, defaults to `go test -v ./...`
])
```

## JavaScript

Runs tests for JavaScript projects. Needs to implement `npm test`/`yarn test` in order to work. Runs tests on Ubuntu, macOS and Windows for NodeJS version 8.9.1 and 9.2.0

### Example

```groovy
javascript()
```

## Website Preview and Deployment (hugo only)

Builds commits and publishes previews over IPFS for websites using hugo. If commit is on master branch, deploys that commit via dnslink-dnsimple as well.

### Example

```groovy
website([
  website: 'libp2p.io', // required argument for which zone this website will deployed at
  record: '_dnslink' // required argument for which record to add the dnslink TXT record at
])
```

## License

MIT 2017
