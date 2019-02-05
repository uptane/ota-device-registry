package com.advancedtelematic.ota.deviceregistry.db

import java.time.Instant

import com.advancedtelematic.libats.data.DataType.CorrelationId
import com.advancedtelematic.libats.data.{EcuIdentifier, PaginationResult}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, EcuInstallationReport}
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs.deviceInstallationReportDecoder
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceInstallationReport
import com.advancedtelematic.libats.slick.db.SlickAnyVal._
import com.advancedtelematic.libats.slick.db.SlickCirceMapper._
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
import com.advancedtelematic.libats.slick.db.SlickUrnMapper.correlationIdMapper
import com.advancedtelematic.libats.slick.db.SlickValidatedGeneric.validatedStringMapper
import com.advancedtelematic.ota.deviceregistry.data.DataType.{DeviceInstallationResult, EcuInstallationResult, InstallationStat}
import com.advancedtelematic.ota.deviceregistry.data.Device.DeviceOemId
import com.advancedtelematic.ota.deviceregistry.db.DbOps.PaginationResultOps
import io.circe.Json
import slick.jdbc.MySQLProfile.api._
import slick.lifted.AbstractTable

import scala.concurrent.ExecutionContext

object InstallationReportRepository {

  trait InstallationResultTable {
    def correlationId: Rep[CorrelationId]
    def resultCode: Rep[String]
    def success: Rep[Boolean]
  }

  class DeviceInstallationResultTable(tag: Tag)
    extends Table[DeviceInstallationResult](tag, "DeviceInstallationResult") with InstallationResultTable {

    def correlationId = column[CorrelationId]("correlation_id")
    def resultCode    = column[String]("result_code")
    def deviceUuid    = column[DeviceId]("device_uuid")
    def success = column[Boolean]("success")
    def receivedAt = column[Instant]("received_at")
    def installationReport = column[Json]("installation_report")

    def * =
      (correlationId, resultCode, deviceUuid, success, receivedAt, installationReport) <>
      ((DeviceInstallationResult.apply _).tupled, DeviceInstallationResult.unapply)

    def pk = primaryKey("pk_device_report", (correlationId, deviceUuid))
  }

  private val deviceInstallationResults = TableQuery[DeviceInstallationResultTable]

  class EcuInstallationResultTable(tag: Tag)
    extends Table[EcuInstallationResult](tag, "EcuInstallationResult") with InstallationResultTable {

    def correlationId = column[CorrelationId]("correlation_id")
    def resultCode    = column[String]("result_code")
    def deviceUuid    = column[DeviceId]("device_uuid")
    def ecuId     = column[EcuIdentifier]("ecu_id")
    def success = column[Boolean]("success")

    def * =
      (correlationId, resultCode, deviceUuid, ecuId, success) <>
      ((EcuInstallationResult.apply _).tupled, EcuInstallationResult.unapply)

    def pk = primaryKey("pk_ecu_report", (deviceUuid, ecuId))
  }

  private val ecuInstallationResults = TableQuery[EcuInstallationResultTable]

  def saveInstallationResults(correlationId: CorrelationId,
                              deviceUuid: DeviceId,
                              deviceResultCode: String,
                              success: Boolean,
                              ecuReports: Map[EcuIdentifier, EcuInstallationReport],
                              receivedAt: Instant,
                              installationReport: Json)(implicit ec: ExecutionContext): DBIO[Unit] = {

    val deviceResult = DeviceInstallationResult(correlationId, deviceResultCode, deviceUuid, success, receivedAt, installationReport)
    val ecuResults = ecuReports.map {
      case (ecuId, ecuReport) => EcuInstallationResult(correlationId, ecuReport.result.code, deviceUuid, ecuId, ecuReport.result.success)
    }
    val q =
      for {
        _ <- deviceInstallationResults.insertOrUpdate(deviceResult)
        _ <- DBIO.sequence(ecuResults.map(ecuInstallationResults.insertOrUpdate))
      } yield ()
    q.transactionally
  }

  private def statsQuery[T <: AbstractTable[_]](tableQuery: TableQuery[T], correlationId: CorrelationId)
                                               (implicit ec: ExecutionContext, ev: T <:< InstallationResultTable): DBIO[Seq[InstallationStat]] = {
    tableQuery
      .map(r => (r.correlationId, r.resultCode, r.success))
      .filter(_._1 === correlationId)
      .groupBy(r => (r._2, r._3))
      .map(r => (r._1._1, r._2.length, r._1._2))
      .result
      .map(_.map(stat => InstallationStat(stat._1, stat._2, stat._3)))
  }

  def installationStatsPerDevice(correlationId: CorrelationId)(implicit ec: ExecutionContext): DBIO[Seq[InstallationStat]] =
    statsQuery(deviceInstallationResults, correlationId)

  def installationStatsPerEcu(correlationId: CorrelationId)(implicit ec: ExecutionContext): DBIO[Seq[InstallationStat]] =
    statsQuery(ecuInstallationResults, correlationId)

  def fetchDeviceInstallationReport(correlationId: CorrelationId)(implicit ec: ExecutionContext): DBIO[Seq[DeviceInstallationResult]] =
    deviceInstallationResults.filter(_.correlationId === correlationId).result

  def fetchEcuInstallationReport(correlationId: CorrelationId)(implicit ec: ExecutionContext): DBIO[Seq[EcuInstallationResult]] =
    ecuInstallationResults.filter(_.correlationId === correlationId).result

  def fetchInstallationHistory(deviceId: DeviceId, offset: Option[Long], limit: Option[Long])
                              (implicit ec: ExecutionContext): DBIO[PaginationResult[Json]] =
    deviceInstallationResults
      .filter(_.deviceUuid === deviceId)
      .map(_.installationReport)
      .paginateResult(offset.orDefaultOffset, limit.orDefaultLimit)

  def fetchDeviceFailures(correlationId: CorrelationId)(implicit ec: ExecutionContext): DBIO[Seq[(DeviceOemId, String, String)]] =
      deviceInstallationResults
        .filter(_.correlationId === correlationId)
        .filter(_.success === false)
        .join(DeviceRepository.devices)
        .on(_.deviceUuid === _.uuid)
        .map { case (r, d) => (d.deviceId, r.resultCode, r.installationReport) }
        .result
        .map(_.map { case (deviceOemId, resultCode, report) => (
          deviceOemId,
          resultCode,
          report.as[DeviceInstallationReport].fold(_ => "UNDEFINED", _.result.description))
        })


}