package com.bwsw.thriftFinaglePoC.scroogeServer

import com.twitter.finagle.Thrift
import com.twitter.util.Await

object ScroogeServer extends App {

  val service = Thrift.server.serveIface(":9099", new Service())
  Await.ready(service)

}
