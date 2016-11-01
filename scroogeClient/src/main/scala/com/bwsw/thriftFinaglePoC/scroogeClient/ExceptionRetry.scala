package com.bwsw.thriftFinaglePoC.scroogeClient

import com.bwsw.thriftFinaglePoC.service.SampleService.Add
import com.bwsw.thriftFinaglePoC.service.{SampleService, ServiceException}
import com.twitter.finagle.service.{RetryExceptionsFilter, TimeoutFilter}
import com.twitter.finagle.thrift.ThriftServiceIface
import com.twitter.finagle.util.HashedWheelTimer
import com.twitter.finagle.{Filter, IndividualRequestTimeoutException, Service, Thrift}
import com.twitter.util.{Duration, Future, Throw}

/**
  * Represents case of sending single [[com.bwsw.thriftFinaglePoC.service.SampleService.Add]] request to server
  * and applying to it Finagle filters to achieve following properties:
  * Logs failed timeouts to standard output and retries request
  * Catches and logs [[com.bwsw.thriftFinaglePoC.service.ServiceException]] to standard output and retries request
  * @param serviceIface Iface of [[com.bwsw.thriftFinaglePoC.service.SampleService]]
  * @param timeout Timeout duration
  * @param backoff Stream representing backoff strategy
  * @see [[com.twitter.finagle.service.Backoff]]
  */
class ExceptionRetry(serviceIface: SampleService.ServiceIface, timeout: Duration, backoff: Stream[Duration]) {

  val timeoutFilter = new TimeoutFilter[SampleService.Add.Args, SampleService.Add.Result](timeout, HashedWheelTimer.Default)

  val retryFilter: RetryExceptionsFilter[SampleService.Add.Args, SampleService.Add.SuccessType] = RetryExceptionsFilter(backoff) {
    //Default timeout exception
    case Throw(_: IndividualRequestTimeoutException) => println("Failed timeout. Trying again"); true
    //My custom thrift exception (It is not com.twitter.finagle.ServiceException!)
    case Throw(_: ServiceException) => println("They didn't sum my numbers this time. Trying again"); true
  } (HashedWheelTimer.Default)

  val filteredServiceIface = serviceIface.copy(
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
      timeoutFilter andThen
      serviceIface.add
  )

  val client = Thrift.client.newMethodIface(filteredServiceIface)

  /**
    * Send [[com.bwsw.thriftFinaglePoC.service.SampleService.Add]] request to server
    * @return Future of result of adding x1 and x2
    */
  def send(x1: Int, x2: Int): Future[Int] = client.add(x1, x2)

}

object ExceptionRetry {

  def apply(service: SampleService.ServiceIface,
            timeout: Duration,
            backoff: Stream[Duration]) =
    new ExceptionRetry(service: SampleService.ServiceIface,
      timeout: Duration,
      backoff: Stream[Duration])

}
