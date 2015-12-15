resolvers += Resolver.url("bintray-productfoundry-releases", url("http://dl.bintray.com/productfoundry/"))(Resolver.ivyStylePatterns)

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")