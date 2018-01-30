package lib

// Resolve takes a website and record, and makes it into a URL that can be
// resolved with `ipfs resolve $DOMAIN`, removing _dnslink if needed
static String Resolve(opts = []) {
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
