/*
 * Copyright 2018 HM Revenue & Customs
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

package connectors

import audit.Auditable
import config.{MicroserviceAuditConnector, WSHttp}
import metrics.{Metrics, MetricsEnum}
import models._
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.config.{AppName, ServicesConfig}
import utils.GovernmentGatewayConstants

import scala.concurrent.ExecutionContext.Implicits.global

trait TaxEnrolmentsConnector extends ServicesConfig with RawResponseReads with Auditable {

  def serviceUrl: String

  def emacBaseUrl: String

  def metrics: Metrics

  def http: CorePut

  def addKnownFacts(verifiers: Verifiers, atedRefNo: String)(implicit headerCarrier: HeaderCarrier) = {

    val atedRefIdentifier = "ATEDRefNumber"
    val enrolmentKey = s"${GovernmentGatewayConstants.AtedServiceName}~$atedRefIdentifier~$atedRefNo"
    val putUrl = s"$emacBaseUrl/$enrolmentKey"

    val timerContext = metrics.startTimer(MetricsEnum.EmacAddKnownFacts)
    http.PUT[JsValue, HttpResponse](putUrl, Json.toJson(verifiers)) map { response =>
      timerContext.stop()
      auditAddKnownFacts(putUrl, verifiers, response)
      response.status match {
        case NO_CONTENT =>
          metrics.incrementSuccessCounter(MetricsEnum.EmacAddKnownFacts)
          response
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EmacAddKnownFacts)
          Logger.warn(s"[TaxEnrolmentsConnector][addKnownFacts] - status: $status")
          doFailedAudit("addKnownFacts", verifiers.toString, response.body)
          response
      }
    }
  }

  private def auditAddKnownFacts(putUrl: String, verifiers: Verifiers, response: HttpResponse)(implicit hc: HeaderCarrier) = {
    val status = response.status match {
      case NO_CONTENT => EventTypes.Succeeded
      case _ => EventTypes.Failed
    }
    sendDataEvent(transactionName = "emacAddKnownFacts",
      detail = Map("txName" -> "emacAddKnownFacts",
        "serviceName" -> s"${GovernmentGatewayConstants.AtedServiceName}",
        "putUrl" -> s"$putUrl",
        "requestBody" -> s"${Json.prettyPrint(Json.toJson(verifiers))}",
        "responseStatus" -> s"${response.status}",
        "responseBody" -> s"${response.body}",
        "status" -> s"$status"))
  }
}

object TaxEnrolmentsConnector extends TaxEnrolmentsConnector {
  val serviceUrl = baseUrl("tax-enrolments")
  val emacBaseUrl = s"$serviceUrl/tax-enrolments"
  val metrics = Metrics
  val http = WSHttp
  val audit: Audit = new Audit(AppName.appName, MicroserviceAuditConnector)
  val appName: String = AppName.appName
}
