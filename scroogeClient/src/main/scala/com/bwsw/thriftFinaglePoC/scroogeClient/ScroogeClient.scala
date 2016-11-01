package com.bwsw.thriftFinaglePoC.scroogeClient

import collection.JavaConverters._
import collection.mutable
import com.bwsw.thriftFinaglePoC.service.SampleService
import com.bwsw.thriftFinaglePoC.service.SampleService.Add
import com.bwsw.thriftFinaglePoC.service.ServiceException
import com.bwsw.thriftFinaglePoC.struct.SampleStruct
import com.twitter.conversions.time._
import com.twitter.finagle.service.{Backoff, RetryExceptionsFilter, TimeoutFilter}
import com.twitter.finagle.thrift.ThriftServiceIface
import com.twitter.finagle.util.HashedWheelTimer
import com.twitter.finagle._
import com.twitter.util._
import com.typesafe.config.ConfigFactory

import scala.util.Random

object ScroogeClient extends App {

  if (args.length < 4) {
    println("Usage: \"project scroogeClient\" \"run <T> <Rmin> <Rmax> <M>\"")
    println("Where T is timeout in mills, Rmin/Rmax is min/max latency mills between retries (increases exponentially by a factor of two)")
    println("M is number of responses to wait for ignoring the rests; M <= N. N is the number of instantiated servers which are taken from configuration file")
    System.exit(1)
  }

  val conf = ConfigFactory.load()
  val ports = conf.getStringList("server-ports").asScala

  val (t, rmin, rmax, m) = (args(0).toInt, args(1).toInt, args(2).toInt, args(3).toInt)

  /*
    We don't need to log mess from some exceptions (Timeout in this example).
    See http://twitter.github.io/finagle/guide/Clients.html#observability
    */
  val ignoreMonitor = new Monitor {
    def handle(t: Throwable): Boolean = true
  }

  val clientServiceIfaces = for (port <- ports)
    yield Thrift.client
      .withMonitor(ignoreMonitor)
      .newServiceIface[SampleService.ServiceIface](s":$port", s"foo#$port")

  val backoff = Backoff.exponential(rmin.milliseconds, 2, rmax.milliseconds)

  val addTimeoutFilter = new TimeoutFilter[SampleService.Add.Args, SampleService.Add.Result](t.milliseconds, HashedWheelTimer.Default)

  val addRetryFilter: RetryExceptionsFilter[SampleService.Add.Args, SampleService.Add.SuccessType] = RetryExceptionsFilter(backoff) {
    //Default timeout exception
    case Throw(_: IndividualRequestTimeoutException) => println("Failed timeout. Trying again"); true
    //My custom thrift exception (It is not com.twitter.finagle.ServiceException!)
    case Throw(_: ServiceException) => println("They didn't sum my numbers this time. Trying again"); true
  } (HashedWheelTimer.Default)

  val addServiceIface = clientServiceIfaces.head.copy(
    //Filters are applied from right to left.
    add = new Filter[Add.Args, Add.Result, Add.Args, Add.SuccessType] {
      /*
      This filter's only purpose is to map Thrift's operation-depended return types
      Which are changed in exception handling filters' contexts (see retryFilter type)
      */
      override def apply(request: Add.Args, service: Service[Add.Args, Int]): Future[Add.Result] = {
        service(request).map(x => Add.Result(Some(x)))
      }
    } andThen
      addRetryFilter andThen
      ThriftServiceIface.resultFilter(Add) andThen
      addTimeoutFilter andThen
      clientServiceIfaces.head.add
  )

  val addClient = Thrift.client.newMethodIface(addServiceIface)

  println(s"2 + 2 is ${Await.result(addClient.add(2, 2))}")


  val raceClients = clientServiceIfaces.map(Thrift.client.newMethodIface(_))

  var totalSends = 0
  val completedSoFar: mutable.Map[SampleStruct, SampleService[Future]] = mutable.Map()
  var done = false

  def send(messages: Seq[(SampleService[Future], (Int, String))]) =
    for ((raceClient, (key, value)) <- messages) yield {
      raceClient.createStruct(key, value) onSuccess { struct =>
        if (completedSoFar.size < m) {
          completedSoFar += ((struct, raceClient))
          if (completedSoFar.size >= m)
            done = true
        }
      }
      totalSends += 1
    }

  val timer = HashedWheelTimer.Default
  timer.schedule(t.milliseconds) {
    send(raceClients
      .filterNot(raceClient => completedSoFar.values.exists(_ == raceClient))
      .map { raceClient =>
        val key = Random.nextInt(500)
        (raceClient, (key, s"struct#$key"))
      })
  }

  while (!done) {
    Thread.sleep(100)
  }

  timer.stop()

  println(s"Got first $m structs:")
  println(completedSoFar.keys.map(_.toString).mkString("\n"))
  println(s"Total sends: $totalSends")

}
