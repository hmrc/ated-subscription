/*
 * Copyright 2016 HM Revenue & Customs
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
import config.{MicroserviceAuditConnector, WSHttp}
import metrics.{Metrics, MetricsEnum}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.JsValue
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.config.{AppName, ServicesConfig}
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ETMPConnector extends ServicesConfig with RawResponseReads with Auditable {

  def serviceURL: String

  def baseURI: String

  def subscribeUri: String

  def urlHeaderEnvironment: String

  def urlHeaderAuthorization: String

  def metrics: Metrics

  def http: HttpGet with HttpPost

  def subscribeAted(data: JsValue)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    implicit val hc = createHeaderCarrier

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
            Logger.warn(s"[ETMPConnector][subscribeAted] - status: $status Error ${response.body}")
            response
        }
    }
  }

  private def auditSubscribe(data: JsValue, response: HttpResponse)(implicit hc: HeaderCarrier) = {
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
        "responseBody" -> s"${response.body}"),
      eventType = eventType)
  }

  def createHeaderCarrier(): HeaderCarrier = {
    HeaderCarrier(extraHeaders = Seq("Environment" -> urlHeaderEnvironment),
      authorization = Some(Authorization(urlHeaderAuthorization)))
  }

}

object ETMPConnector extends ETMPConnector {
  val serviceURL = baseUrl("etmp-hod")
  val baseURI = "annual-tax-enveloped-dwellings"
  val subscribeUri = "subscribe"
  val urlHeaderEnvironment: String = config("etmp-hod").getString("environment").getOrElse("")
  val urlHeaderAuthorization: String = s"Bearer ${config("etmp-hod").getString("authorization-token").getOrElse("")}"
  val audit: Audit = new Audit(AppName.appName, MicroserviceAuditConnector)
  val http = WSHttp
  val appName: String = AppName.appName
  val metrics = Metrics
}
