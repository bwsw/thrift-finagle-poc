package com.bwsw.thriftFinaglePoC.scroogeClient

import com.bwsw.thriftFinaglePoC.service.SampleService
import com.twitter.conversions.time._
import com.twitter.finagle._
import com.twitter.finagle.service.Backoff
import com.twitter.util._
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

object ScroogeClient extends App {

  if (args.length < 5) {
    println("Usage: \"project scroogeClient\" \"run <T> <Rmin> <Rmax> <Rfactor> <M>\"")
    println("Where T is timeout in mills, Rmin/Rmax is min/max latency mills between retries (increases exponentially by a factor of two)")
    println("M is number of responses to wait for ignoring the rests; M <= N. N is the number of instantiated servers which are taken from configuration file")
    System.exit(1)
  }

  val conf = ConfigFactory.load()
  val ports = conf.getStringList("server-ports").asScala
  val (t, rmin, rmax, rfactor, m) = (args(0).toInt, args(1).toInt, args(2).toInt, args(3).toInt, args(4).toInt)


  val clientServiceIfaces = for (port <- ports)
    yield Thrift.client
      /*
      We don't need to log mess from some exceptions (Timeout in this example).
      See http://twitter.github.io/finagle/guide/Clients.html#observability
      */
    .withMonitor(new Monitor {
      def handle(t: Throwable): Boolean = true
    })
    .newServiceIface[SampleService.ServiceIface](s":$port", s"foo#$port")

  val backoff = Backoff.exponential(rmin.milliseconds, rfactor, rmax.milliseconds)

  val exceptionRetryCase = ExceptionRetry(clientServiceIfaces.head, t.milliseconds, backoff)

  println(s"2 + 2 is ${Await.result(exceptionRetryCase.send(2, 2))}")

  val groupRequestCase = GroupRequest(clientServiceIfaces, m, t.milliseconds)

  val result = groupRequestCase.send().mkString("\n")
  println(s"Got first $m structs:")
  println(result)

}
