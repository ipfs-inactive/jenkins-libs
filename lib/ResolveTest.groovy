import static lib.Resolve.Resolve

def createTestCase(website, record, output) {
  return [website, record, output]
}

def catchError(arg) {
  try {
    Resolve(arg)
    return null
  } catch (AssertionError err) {
    return err
  }
}

def testCases = [
  // website, record, expected output
  createTestCase('multiformats.io', '_dnslink', 'multiformats.io'),
  createTestCase('ipfs.io', '', 'ipfs.io'),
  createTestCase('ipfs.io', null, 'ipfs.io'),
  createTestCase('ipfs.io', '_dnslink', 'ipfs.io'),
  createTestCase('docs.ipfs.io', '_dnslink.beta', 'beta.docs.ipfs.io')
]

// Test normal cases
for (c in testCases) {
  def opts = [
    website: c[0],
    record: c[1]
  ]
  def expectedOutput = c[2]
  assert Resolve(opts) == expectedOutput
}

// Should fail if not passing in website
assert catchError([record: '_dnslink']) : "Error should have been thrown"

// Should fail if not passing any args
assert catchError([]) : "Error should have been thrown"
