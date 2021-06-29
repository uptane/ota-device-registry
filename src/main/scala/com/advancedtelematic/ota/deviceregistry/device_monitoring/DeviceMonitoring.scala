package com.advancedtelematic.ota.deviceregistry.device_monitoring

import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.ota.deviceregistry.device_monitoring.DataType.MonitoringPayload
import io.circe.{Decoder, Json}
import slick.collection.heterogeneous.HNil
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future
import scala.language.implicitConversions

class DeviceMonitoring()(implicit db: DeviceMonitoringDB) {
  def parse(ns: Namespace, deviceId: DeviceId, payload: Json): Decoder.Result[MonitoringPayload] =
    payload.as[MonitoringPayload](MonitoringPayload.decoder(ns, deviceId))

  def persist(data: MonitoringPayload, raw: Json): Future[Int] = db.run {
      (Schema.monitoringObservations += data).andThen(Schema.monitoringRawObservations += (data.ns :: data.deviceId :: raw :: data.observedAt :: HNil))
    }
}
