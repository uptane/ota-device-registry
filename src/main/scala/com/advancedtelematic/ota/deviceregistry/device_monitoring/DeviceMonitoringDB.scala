package com.advancedtelematic.ota.deviceregistry.device_monitoring

import com.advancedtelematic.libats.data.UUIDKey.UUIDKey
import io.circe.{Decoder, Encoder}
import slick.ast.BaseTypedType
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcType
import slick.jdbc.PostgresProfile.api._

import java.util.UUID
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.reflect.ClassTag

object DeviceMonitoringDB {
  def fromConfig(): DeviceMonitoringDB = {
    val db = Database.forConfig("device_monitoring.db")
    new DeviceMonitoringDB(db)
  }

  implicit def toDatabase(db: DeviceMonitoringDB): slick.jdbc.PostgresProfile.api.Database = db.db
}

class DeviceMonitoringDB(protected[device_monitoring] val db: slick.jdbc.PostgresProfile.api.Database) {
  def migrate(): Future[Int] = {
    val schema =
      sql"""
      CREATE TABLE IF NOT EXISTS device_observations (
        time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
        namespace varchar(255) not NULL,
        device_uuid uuid NOT NULL,
        cpu_p double PRECISION NULL,
        temp double PRECISION NULL,
        mem_used double PRECISION NULL,
        mem_total double PRECISION NULL,
        mem_free double PRECISION NULL,
        swap_used double PRECISION NULL,
        swap_total double PRECISION NULL,
        swap_free double PRECISION NULL,
        docker_alive boolean NULL,
        created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT current_timestamp
    );

      CREATE TABLE IF NOT EXISTS device_raw_observations (
        time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
        namespace varchar(255) not NULL,
        device_uuid uuid NOT NULL,
        payload jsonb NULL
    );

     select create_hypertable('device_observations', 'time', if_not_exists => true);
    """.asUpdate

    db.run(schema)
  }
}

protected [device_monitoring] object SlickPgMappings {
  import cats.syntax.either._
  import io.circe.syntax._
  import shapeless._

  implicit def dbMapping[T <: UUIDKey]
  (implicit gen: Generic.Aux[T, UUID :: HNil], ct: ClassTag[T]): JdbcType[T] with BaseTypedType[T] =
    MappedColumnType.base[T, UUID](_.uuid, (s: UUID) => gen.from(s:: HNil))

  // Would be better to use PgObject here, but don't want to pull slick-pg just for this
  def jsonMapping[T : Encoder : Decoder : ClassTag] = MappedColumnType.base[T, String](
    _.asJson.noSpaces,
    str => io.circe.parser.decode(str).valueOr(throw _)
  )
}
