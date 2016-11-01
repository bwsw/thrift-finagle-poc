package com.bwsw.thriftFinaglePoC.scroogeServer

import com.bwsw.thriftFinaglePoC.struct.SampleStruct

import collection.JavaConverters._
import com.twitter.finagle.Thrift
import com.twitter.util.{Await, Future}
import com.typesafe.config.ConfigFactory

object ScroogeServer extends App {

  val conf = ConfigFactory.load()
  val ports = conf.getStringList("server-ports").asScala

  //Instantiate N servers where N is number of ports in configuration file
  val services = ports.zipWithIndex.map { case (port, index) =>
    Thrift.server.serveIface(s":$port", new Service() {
      override def createStruct(key: Int, value: String): Future[SampleStruct] = {
        Thread.sleep((key * port.toInt % 5) * 1000)
        super.createStruct(key, value)
      }
    })
  }

  Await.ready(Future.collect(
    services.map(service => Future(Await.ready(service)))
  ))

}
