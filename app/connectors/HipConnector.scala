/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors

import audit.Auditable

import javax.inject.Inject
import metrics.{MetricsEnum, ServiceMetrics}
import play.api.Logging
import play.api.http.Status
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.EventTypes
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits._
import utils.HipUtilities

import java.time.{ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.{Base64, UUID}
import scala.concurrent.{ExecutionContext, Future}

class DefaultHipConnector @Inject()(val servicesConfig: ServicesConfig,
                                     val auditConnector: AuditConnector,
                                     val metrics: ServiceMetrics,
                                     val http: HttpClientV2) extends HipConnector {

  override val serviceUri: String = servicesConfig.baseUrl("hip")
  override val clientId: String = servicesConfig.getConfString("hip.clientId", "")
  override val clientSecret: String = servicesConfig.getConfString("hip.clientSecret", "")
  override val originatingSystem: String = servicesConfig.getConfString("hip.originatingSystem", "ATED")
  override val baseUri: String = "etmp/RESTAdapter/ated"
  override val subscribeUri: String = "subscription"
}

trait HipConnector extends Auditable with Logging {

  def serviceUri: String
  def baseUri: String
  def subscribeUri: String
  def metrics: ServiceMetrics
  def http: HttpClientV2
  val clientId: String
  val clientSecret: String
  val originatingSystem: String
  val transmittingSystem: String = "HIP"
  val authorizationToken: String = Base64.getEncoder.encodeToString(s"$clientId:$clientSecret".getBytes("UTF-8"))

  def headers: Seq[(String, String)] = Seq(
    "correlationid" -> UUID.randomUUID().toString,
    "X-Originating-System" -> originatingSystem,
    "X-Receipt-Date" -> retrieveCurrentTime,
    "X-Transmitting-System" -> transmittingSystem,
    "Authorization" -> s"Basic $authorizationToken"
  )

  private def retrieveCurrentTime: String = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    formatter.format(ZonedDateTime.now(ZoneId.of("UTC")))
  }

  def subscribeAted(data: JsValue)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    val timerContext = metrics.startTimer(MetricsEnum.EtmpSubscribeAted)
    val postUrl=s"$serviceUri/$baseUri/$subscribeUri"
    val withAcknowledgementReferenceRemovedJson = HipUtilities.removeAcknowledgementReferenceField(data)
    http.post(url"$postUrl").withBody(withAcknowledgementReferenceRemovedJson).setHeader(headers: _*).execute[HttpResponse].map{ response =>
      timerContext.stop()
      auditSubscribe(withAcknowledgementReferenceRemovedJson, response)
      response.status match {
        case CREATED =>
          val stripSuccessJson = HipUtilities.stripSuccessWrapper(response.json)
          metrics.incrementSuccessCounter(MetricsEnum.EtmpSubscribeAted)
          HttpResponse(
            status = OK,
            body = Json.stringify(stripSuccessJson),
            headers = response.headers
          )
        case UNPROCESSABLE_ENTITY =>

          val badRequestErrorCodes = Set("001", "002", "003")

          HipUtilities.extractHipErrorCode(response.body) match {
            case Some((code, text)) if badRequestErrorCodes.contains(code) =>
              metrics.incrementFailedCounter(MetricsEnum.EtmpSubscribeAted)
              doFailedAudit("subscribeAtedFailed", postUrl, response.body)
              logger.error(s"[HipConnector][subscribeAted] - $text")
              HttpResponse(
                status = Status.BAD_REQUEST,
                body = response.body,
                headers = response.headers
              )

            case status@_ =>
              metrics.incrementFailedCounter(MetricsEnum.EtmpSubscribeAted)
              doFailedAudit("subscribeAtedFailed", postUrl, response.body)
              logger.error(s"[EtmpConnector][subscribeAted] - Unsuccessful return of data. Status code: $status")
              HttpResponse(
                status = Status.INTERNAL_SERVER_ERROR,
                body = response.body,
                headers = response.headers
              )
          }
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EtmpSubscribeAted)
          doFailedAudit("subscribeAtedFailed", data.toString, response.body)
          logger.error(s"[ETMPConnector][subscribeAted]: HttpStatus:$status :: SessionId = ${headerCarrier.sessionId} :: " +
            s"Response from ETMP: ${response.body}")
          response
      }
    }
  }

  private def auditSubscribe(data: JsValue, response: HttpResponse)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    val eventType = response.status match {
      case OK => EventTypes.Succeeded
      case _ => EventTypes.Failed
    }
    val atedRefNumber = (data \ "atedRefNumber").asOpt[String].getOrElse("")
    val safeId = (data \ "safeId").asOpt[String].getOrElse("")

    sendDataEvent(transactionName = "etmpSubscribe",
      detail = Map("txName" -> "etmpSubscribe",
        "atedRefNumber" -> s"$atedRefNumber",
        "safeId" -> s"$safeId",
        "request" -> s"$data",
        "responseStatus" -> s"${response.status}",
        "responseBody" -> s"${response.body}",
        "status" -> s"$eventType"))

    def getAddressPiece(piece: Option[JsValue]): String = {
      piece match {
        case Some(x) => x.toString()
        case _       => ""
      }
    }

    val postcodeOption = (data \\ "postalCode").headOption

    sendDataEvent(transactionName = if (postcodeOption.isDefined) "manualAddressSubmitted" else "internationalAddressSubmitted",
      detail = Map(
        "submittedLine1" -> (data \\ "addressLine1").head.as[String],
        "submittedLine2" -> (data \\ "addressLine2").head.as[String],
        "submittedLine3" -> getAddressPiece((data \\ "addressLine3").headOption),
        "submittedLine4" -> getAddressPiece((data \\ "addressLine4").headOption),
        "submittedPostcode" -> getAddressPiece((data \\ "postalCode").headOption),
        "submittedCountry" -> (data \\ "countryCode").head.as[String]))
  }

}