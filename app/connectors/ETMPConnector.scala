/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.JsValue
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DefaultETMPConnector @Inject()(val servicesConfig: ServicesConfig,
                                     val auditConnector: AuditConnector,
                                     val metrics: ServiceMetrics,
                                     val http: HttpClient) extends ETMPConnector {
  val serviceURL = servicesConfig.baseUrl("etmp-hod")
  val baseURI = "annual-tax-enveloped-dwellings"
  val subscribeUri = "subscribe"
  val urlHeaderEnvironment: String = servicesConfig.getConfString("etmp-hod.environment", "")
  val urlHeaderAuthorization: String = s"Bearer ${servicesConfig.getConfString("etmp-hod.authorization-token", "")}"
  val audit: Audit = new Audit("ated-subscription", auditConnector)
}

trait ETMPConnector extends RawResponseReads with Auditable {

  def serviceURL: String
  def baseURI: String
  def subscribeUri: String
  def urlHeaderEnvironment: String
  def urlHeaderAuthorization: String
  def metrics: ServiceMetrics
  def http: HttpClient

  def subscribeAted(data: JsValue)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    implicit val hc = createHeaderCarrier()
    val timerContext = metrics.startTimer(MetricsEnum.EtmpSubscribeAted)
    http.POST[JsValue, HttpResponse](s"$serviceURL/$baseURI/$subscribeUri", data) map {
      response =>
        val stopContext = timerContext.stop()
        auditSubscribe(data, response)
        response.status match {
          case OK =>
            metrics.incrementSuccessCounter(MetricsEnum.EtmpSubscribeAted)
            response
          case status =>
            metrics.incrementFailedCounter(MetricsEnum.EtmpSubscribeAted)
            Logger.warn(s"[ETMPConnector][subscribeAted] - status: $status")
            doFailedAudit("subscribeAtedFailed", data.toString, response.body)
            response
        }
    }
  }

  private def auditSubscribe(data: JsValue, response: HttpResponse)(implicit hc: HeaderCarrier): Unit = {
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

  def createHeaderCarrier(): HeaderCarrier = {
    HeaderCarrier(extraHeaders = Seq("Environment" -> urlHeaderEnvironment),
      authorization = Some(Authorization(urlHeaderAuthorization)))
  }

}
