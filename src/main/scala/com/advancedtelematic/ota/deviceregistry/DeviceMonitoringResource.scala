package com.advancedtelematic.ota.deviceregistry

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive1, Route}
import com.advancedtelematic.libats.messaging_datatype.DataType
import com.advancedtelematic.ota.deviceregistry.device_monitoring.{DeviceMonitoring, DeviceMonitoringDB}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json
import cats.syntax.either._
import com.advancedtelematic.libats.data.DataType.Namespace
import org.slf4j.LoggerFactory


class DeviceMonitoringResource(namespaceExtractor: Directive1[Namespace],
                               deviceNamespaceAuthorizer: Directive1[DataType.DeviceId])(implicit monitoringDB: DeviceMonitoringDB, system: ActorSystem) {
  import akka.http.scaladsl.server.Directives._

  import system.dispatcher

  val deviceMonitoring = new DeviceMonitoring

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  val route: Route =
    (pathPrefix("devices") & namespaceExtractor) { ns =>
      deviceNamespaceAuthorizer { uuid =>
        path("monitoring") {
          (post & entity(as[Json])) { payload =>
            log.debug("device observation from client: {}", payload.noSpaces)

            val parsed = deviceMonitoring.parse(ns, uuid, payload).valueOr(throw _)
            val f = deviceMonitoring.persist(parsed, payload).map(_ => StatusCodes.NoContent)
            complete(f)
          }
        }
      }
    }
}

class NooDeviceMonitoringResource(deviceNamespaceAuthorizer: Directive1[DataType.DeviceId]) {
  import akka.http.scaladsl.server.Directives._

  val route: Route = {
    (pathPrefix("devices") & extractLog) { log =>
      deviceNamespaceAuthorizer { uuid =>
        path("monitoring") {
          log.warning(s"Device $uuid is posting device metrics but this server has device metrics disabled")
          complete(StatusCodes.NoContent)
        }
      }
    }
  }
}