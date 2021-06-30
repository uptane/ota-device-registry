package com.advancedtelematic.ota.deviceregistry.device_monitoring

import cats.syntax.either._
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import io.circe.Decoder

import java.time.Instant
import scala.language.implicitConversions


object DataType {
  case class Memory(free: Long, total: Long, used: Long)

  object Memory {
    implicit def decoder(prefix: String): Decoder[Memory] = io.circe.Decoder.instance { cursor =>
      for {
        memFree <- cursor.downField(s"$prefix.free").as[Long]
        memTotal <- cursor.downField(s"$prefix.total").as[Long]
        memUsed <- cursor.downField(s"$prefix.used").as[Long]
      } yield Memory(memFree, memTotal, memUsed)
    }
  }

  case class MonitoringPayload(ns: Namespace,
                               deviceId: DeviceId,
                               cpu_p: Option[Double],
                               temperature: Option[Double],
                               memory: Option[Memory], swap: Option[Memory],
                               docker_alive: Option[Boolean],
                               observedAt: Instant)

  object MonitoringPayload {
    implicit def decoder(ns: Namespace, deviceId: DeviceId): Decoder[MonitoringPayload] = io.circe.Decoder.instance { cursor =>
      for {
        cpu_p <- cursor.downField("cpu").get[Double]("cpu_p").map(Option(_)).orElse(Right(None))
        temperature <- cursor.downField("temperature").get[Double]("temp").map(Option(_)).orElse(Right(None))
        memory <- cursor.get[Memory]("memory")(Memory.decoder("Mem")).map(Option(_)).orElse(Right(None))
        swap <- cursor.get[Memory]("memory")(Memory.decoder("Swap")).map(Option(_)).orElse(Right(None))
        dockerAlive <- cursor.downField("docker").get[Boolean]("alive").map(Option(_)).orElse(Right(None))
        date <- cursor.get[Double]("date").map { epoch => Instant.ofEpochMilli((epoch * 1000).longValue()) } // Losing some precision here
      } yield MonitoringPayload(ns, deviceId, cpu_p, temperature, memory, swap, dockerAlive, date)
    }
  }
}
