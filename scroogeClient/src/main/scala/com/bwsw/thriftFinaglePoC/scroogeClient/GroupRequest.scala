package com.bwsw.thriftFinaglePoC.scroogeClient

import com.bwsw.thriftFinaglePoC.service.SampleService
import com.bwsw.thriftFinaglePoC.service.SampleService.ServiceIface
import com.bwsw.thriftFinaglePoC.struct.SampleStruct
import com.twitter.finagle.Thrift
import com.twitter.finagle.util.HashedWheelTimer
import com.twitter.util.{Duration, Future}

import scala.collection.mutable
import scala.util.Random

/**
  * Represents case of sending N [[com.bwsw.thriftFinaglePoC.service.SampleService.CreateStruct]] requests,
  * one for each server and waiting for first M for them to complete
  * @param serviceIfaces Ifaces of [[com.bwsw.thriftFinaglePoC.service.SampleService]]
  * @param m Number of answers to wait for
  * @param timeout Duration after reaching which to check for number of answers and resend if this number is less than M
  */
class GroupRequest(serviceIfaces: Seq[SampleService.ServiceIface], m: Int, timeout: Duration) {

  val clients = serviceIfaces.map(Thrift.client.newMethodIface(_))
  val completedSoFar: mutable.Map[SampleStruct, SampleService[Future]] = mutable.Map()
  var done = false

  private def resend(messages: Seq[(SampleService[Future], (Int, String))]) =
    for ((raceClient, (key, value)) <- messages) yield raceClient.createStruct(key, value) onSuccess { struct =>
      if (completedSoFar.size < m) {
        completedSoFar += ((struct, raceClient))
        if (completedSoFar.size >= m)
          done = true
      }
    }

  /**
    * Send [[com.bwsw.thriftFinaglePoC.service.SampleService.CreateStruct]] requests, one for each server of N servers
    * where N is number of service interfaces this class was instantiated with
    * @return M first received [[com.bwsw.thriftFinaglePoC.struct.SampleStruct]]s
    */
  def send(): Seq[SampleStruct] = {
    val timer = HashedWheelTimer.Default
    timer.schedule(timeout) {
      resend(clients
        .filterNot(client => completedSoFar.values.exists(_ == client))
        .map { client =>
          val key = Random.nextInt(500)
          (client, (key, s"struct#$key"))
        })
    }

    while (!done)
      Thread.sleep(100)

    timer.stop()

    completedSoFar.keys.take(m).toSeq
  }

}

object GroupRequest {
  def apply(serviceIfaces: Seq[ServiceIface], m: Int, timeout: Duration) = new GroupRequest(serviceIfaces, m, timeout)
}
