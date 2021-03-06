## This repository has been archived!
*This IPFS-related repository has been archived, and all issues are therefore frozen.* If you want to ask a question or open/continue a discussion related to this repo, please visit the [official IPFS forums](https://discuss.ipfs.io).

We archive repos for one or more of the following reasons:
- Code or content is unmaintained, and therefore might be broken
- Content is outdated, and therefore may mislead readers
- Code or content evolved into something else and/or has lived on in a different place
- The repository or project is not active in general

Please note that in order to keep the primary IPFS GitHub org tidy, most archived repos are moved into the [ipfs-inactive](https://github.com/ipfs-inactive) org.

If you feel this repo should **not** be archived (or portions of it should be moved to a non-archived repo), please [reach out](https://ipfs.io/help) and let us know. Archiving can always be reversed if needed.

# Jenkins Libraries

This repository is being used for setting up default/standard pipelines for projects in https://github.com/ipfs/jenkins

# Issues

Issues are handled via https://github.com/ipfs/testing

# API

A script that sits in `ci/Jenkinsfile` can access these library functions by calling the name of the file and passing arguments.

- [Electron Forge](#electron-forge)
- [Golang](#golang)
- [JavaScript](#javascript)
- [Website](#website-preview-and-deployment-hugo-only)

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

By default, we run tests on Windows, Linux and macOS with NodeJS versions 8.11.1 and 9.2.0
but you can customize it by providing `['nodejs_versions': ['10.0.0']]` to the `javascript()` function.

```groovy
javascript(['nodejs_versions': ['10.0.0']])
```

You can also specify custom versions of node modules in the pipeline.

```groovy
javascript([
    'node_modules': [
        'datastore-fs': 'github:ipfs/js-datastore-fs#fix/node10',
        'datastore-level': 'github:ipfs/js-datastore-level#fix/node10'
    ]
])
```

If you want to run a different command rather than `yarn test`, you can specify
it with `custom_build_steps`. Each step will run in parallel on different workers by default.

```groovy
javascript([
    'custom_build_steps': ['test:browser', 'test:webworker', 'test:node']
])
```

Code coverage reporting via CodeCov is currently activated by default but you can disable
it by setting `coverage` to `false` when calling `javascript()`. When enabled, it'll
run `yarn coverage -u -p codecov` automatically in a separate agent to upload the metrics.

```groovy
javascript([coverage: false])
```


## Website Preview and Deployment (hugo only)

Builds commits and publishes previews over IPFS for websites using hugo. If commit is on master branch, deploys that commit via dnslink-dnsimple as well.

### Example

```groovy
website([
  website: 'libp2p.io', // required argument for which zone this website will deployed at
  record: '_dnslink', // required argument for which record to add the dnslink TXT record at
  build_directory: 'public/', // optional argument for which directory to use as build directory. Defaults to `public/`
  disable_publish: false // optional argument for disabling DNS publish. Useful when websites are using hugo but we're not ready to publish them anywhere yet
])
```

### Example with multiple branches/records

This example will map `release` branch to `_dnslink` record, and `master` branch to `_dnslink.dev` record. This allows us to have development and production deployments, depending on which branch gets updated.

```groovy
website([
  website: 'igis.io',
  record: [
    'release': '_dnslink',
    'master': '_dnslink.dev',
  ]
])
```

## Development

### Replay from CLI

```console
export ORG=IPFS
export REPO=js-ipns
export BRANCH=master
java -jar jenkins-cli.jar -s https://ci.ipfs.team/ replay-pipeline "$ORG/$REPO/$BRANCH/" -s javascript --username VictorBjelkholm --password $GITHUB_TOKEN < vars/javascript.groovy && firefox "$(curl --silent https://ci.ipfs.team/job/$ORG/job/$REPO/job/$BRANCH/lastBuild/api/json | jq -r .url)/console"
```

## License

MIT 2017
