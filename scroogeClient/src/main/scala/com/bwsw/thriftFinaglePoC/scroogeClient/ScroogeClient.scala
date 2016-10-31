package com.bwsw.thriftFinaglePoC.scroogeClient

import com.bwsw.thriftFinaglePoC.service.SampleService
import com.bwsw.thriftFinaglePoC.service.SampleService.Add
import com.bwsw.thriftFinaglePoC.service.ServiceException
import com.bwsw.thriftFinaglePoC.struct.SampleStruct
import com.twitter.conversions.time._
import com.twitter.finagle.service.{Backoff, RetryExceptionsFilter, TimeoutFilter}
import com.twitter.finagle.thrift.ThriftServiceIface
import com.twitter.finagle.util.HashedWheelTimer
import com.twitter.finagle._
import com.twitter.util.{Await, Future, Monitor, Throw}

import scala.collection.mutable.ArrayBuffer

object ScroogeClient extends App {

  if (args.length < 5) {
    println("Usage: \"project scroogeClient\" \"run <T> <Rmin> <Rmax> <N> <M>\"")
    println("Where T is timeout in mills, Rmin/Rmax is min/max latency mills between retries (increases exponentially by a factor of two)")
    println("N is number of total requests to send to server and M is number of responses to wait for ignoring the rests; M <= N")
    System.exit(1)
  }

  val (t, rmin, rmax, n, m) = (args(0).toInt, args(1).toInt, args(2).toInt, args(3).toInt, args(4).toInt)

  val clientServiceIface = Thrift.client
    /*
    We don't need to log mess from some exceptions (Timeout in this example).
    See http://twitter.github.io/finagle/guide/Clients.html#observability
    */
    .withMonitor(new Monitor {
      def handle(t: Throwable): Boolean = true
    })
    .newServiceIface[SampleService.ServiceIface](":9099", "foo")

  val timeoutFilter = new TimeoutFilter[SampleService.Add.Args, SampleService.Add.Result](t.milliseconds, HashedWheelTimer.Default)

  val retryFilter: RetryExceptionsFilter[SampleService.Add.Args, SampleService.Add.SuccessType] = RetryExceptionsFilter(
    Backoff.exponential(rmin.milliseconds, 2, rmax.milliseconds)
  ) {
    //Default timeout exception
    case Throw(_: IndividualRequestTimeoutException) => println("Failed timeout. Trying again"); true
    //My custom thrift exception (It is not com.twitter.finagle.ServiceException!)
    case Throw(_: ServiceException) => println("They didn't sum my numbers this time. Trying again"); true
  } (HashedWheelTimer.Default)

  val retryServiceIface = clientServiceIface.copy(
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
      retryFilter andThen
      ThriftServiceIface.resultFilter(Add) andThen
      clientServiceIface.add
  )

  val retryClient = Thrift.client.newMethodIface(retryServiceIface)

  println(s"2 + 2 is ${Await.result(retryClient.add(2, 2))}")

  val raceClient = Thrift.client.newMethodIface(clientServiceIface)
  val completedSoFar: ArrayBuffer[SampleStruct] = ArrayBuffer()
  var done = false

  for (i <- 1 to n) yield raceClient.createStruct(i, s"Struct #$i") onSuccess { struct =>
    if (completedSoFar.size != m)
      completedSoFar += struct
    else
      done = true
  }

  while (!done) {
    Thread.sleep(100)
  }

  println(s"Got first $m structs:")
  println(completedSoFar.map(_.toString).mkString("\n"))

}
