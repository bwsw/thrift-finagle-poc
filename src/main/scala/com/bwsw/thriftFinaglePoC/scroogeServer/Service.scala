package com.bwsw.thriftFinaglePoC.scroogeServer

import com.bwsw.thriftFinaglePoC.service.{SampleService, ServiceException}
import com.bwsw.thriftFinaglePoC.struct.SampleStruct
import com.twitter.util.Future


class Service extends SampleService[Future] {

  var i = 1
  override def add(num1: Int, num2: Int) = {
    i += 1
    if ((i % 5 != 0) && (num1 < 10 || num2 < 10))
      Future.exception(new ServiceException("We sum numbers with less than two digits only every fifth time"))
    else
      Future.value(num1 + num2)
  }

  override def createStruct(key: Int, value: String) = Future.value(SampleStruct(key, value))

}
