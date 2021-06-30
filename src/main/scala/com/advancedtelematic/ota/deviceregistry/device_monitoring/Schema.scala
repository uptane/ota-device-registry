package com.advancedtelematic.ota.deviceregistry.device_monitoring

import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.ota.deviceregistry.device_monitoring.DataType._
import slick.jdbc.PostgresProfile.api._
import io.circe.Json
import slick.collection.heterogeneous.HNil
import slick.collection.heterogeneous.syntax._

import java.time.Instant
import scala.language.implicitConversions

object Schema {
  import SlickPgMappings._
  import com.advancedtelematic.libats.slick.db.SlickAnyVal._
  import com.advancedtelematic.libats.slick.db.SlickExtensions._

  class DeviceObservations(tag: Tag) extends Table[MonitoringPayload](tag, "device_observations") {
    def namespace = column[Namespace]("namespace")
    def uuid = column[DeviceId]("device_uuid")
    def cpu_p = column[Option[Double]]("cpu_p")
    def temperature = column[Option[Double]]("temp")
    def memory_used = column[Option[Long]]("mem_used")
    def memory_total = column[Option[Long]]("mem_total")
    def memory_free = column[Option[Long]]("mem_free")
    def swap_used = column[Option[Long]]("swap_used")
    def swap_total = column[Option[Long]]("swap_total")
    def swap_free = column[Option[Long]]("swap_free")
    def docker_alive = column[Option[Boolean]]("docker_alive")
    def observed_at = column[Instant]("time")

    def toMonitoringPayload(x: (Namespace, DeviceId, Option[Double], Option[Double], Option[Long], Option[Long], Option[Long], Option[Long], Option[Long], Option[Long], Option[Boolean], Instant)) = x match {
      case (ns, uuid, cpu_p, temperature, memory_free, memory_total, memory_used, swap_free, swap_total, swap_used, docker_alive, observed_at) =>
        def readMem(freeO: Option[Long], totalO: Option[Long], usedO: Option[Long]): Option[Memory] = for {
          free <- freeO
          total <- totalO
          used <- usedO
        } yield Memory(free, total, used)

        MonitoringPayload(ns, uuid, cpu_p, temperature,
          readMem(memory_free, memory_used, memory_total),
          readMem(swap_free, swap_used, swap_total), docker_alive,
          observed_at)
    }

    def fromMonitoringPayload(x: MonitoringPayload) = Some(
      x.ns,
      x.deviceId,
      x.cpu_p,
      x.temperature,
      x.memory.map(_.free),
      x.memory.map(_.total),
      x.memory.map(_.used),
      x.swap.map(_.free),
      x.swap.map(_.total),
      x.swap.map(_.used),
      x.docker_alive,
      x.observedAt
    )

    def * = (namespace, uuid, cpu_p, temperature, memory_free, memory_total, memory_used, swap_free, swap_total, swap_used, docker_alive, observed_at).shaped <> (toMonitoringPayload, fromMonitoringPayload)
  }

  val monitoringObservations = TableQuery[DeviceObservations]

  class DeviceRawObservations(tag: Tag) extends Table[Namespace :: DeviceId :: Json :: Instant :: HNil](tag, "device_raw_observations") {
    implicit val jsonMapping = SlickPgMappings.jsonMapping[Json]

    def namespace = column[Namespace]("namespace")
    def uuid = column[DeviceId]("device_uuid")
    def json = column[Json]("payload")
    def observed_at = column[Instant]("time")

    def * = namespace :: uuid :: json :: observed_at :: HNil
  }

  val monitoringRawObservations = TableQuery[DeviceRawObservations]
}
