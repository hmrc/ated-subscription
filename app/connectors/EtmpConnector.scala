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
import play.api.http.Status._
import play.api.libs.json.JsValue
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.EventTypes
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits._
import scala.concurrent.{ExecutionContext, Future}

class DefaultEtmpConnector @Inject()(val servicesConfig: ServicesConfig,
                                     val auditConnector: AuditConnector,
                                     val metrics: ServiceMetrics,
                                     val http: HttpClientV2) extends EtmpConnector {
  val serviceURL: String = servicesConfig.baseUrl("etmp-hod")
  val baseURI = "annual-tax-enveloped-dwellings"
  val subscribeUri = "subscribe"
  val urlHeaderEnvironment: String = servicesConfig.getConfString("etmp-hod.environment", "")
  val urlHeaderAuthorization: String = s"Bearer ${servicesConfig.getConfString("etmp-hod.authorization-token", "")}"
}

trait EtmpConnector extends Auditable with Logging {

  def serviceURL: String
  def baseURI: String
  def subscribeUri: String
  def urlHeaderEnvironment: String
  def urlHeaderAuthorization: String
  def metrics: ServiceMetrics
  def http: HttpClientV2
  private val regimeURI = "/registration/details"

  private def createHeaders: Seq[(String, String)] = {
    Seq(
      "Environment" -> urlHeaderEnvironment,
      "Authorization" -> urlHeaderAuthorization
    )
  }

  def atedRegime(safeId: String)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    val getUrl = s"""$serviceURL$regimeURI?safeid=$safeId&regime=ATED"""
    http.get(url"$getUrl").setHeader(createHeaders: _*).execute[HttpResponse]
  }

  def subscribeAted(data: JsValue)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    val timerContext = metrics.startTimer(MetricsEnum.EtmpSubscribeAted)
    val postUrl=s"$serviceURL/$baseURI/$subscribeUri"
    http.post(url"$postUrl").withBody(data).setHeader(createHeaders: _*).execute[HttpResponse].map{ response =>
      timerContext.stop()
      auditSubscribe(data, response)
      response.status match {
        case OK =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpSubscribeAted)
          response
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