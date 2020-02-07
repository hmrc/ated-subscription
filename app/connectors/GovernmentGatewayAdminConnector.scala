/*
 * Copyright 2020 HM Revenue & Customs
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
import models._
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import utils.GovernmentGatewayConstants

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DefaultGovernmentGatewayAdminConnector @Inject()(val servicesConfig: ServicesConfig,
                                                       val auditConnector: AuditConnector,
                                                       val metrics: ServiceMetrics,
                                                       val http: HttpClient) extends GovernmentGatewayAdminConnector {
  val serviceURL: String = servicesConfig.baseUrl("government-gateway-admin")
  val addKnownFactsURI = "known-facts"
  override val audit: Audit = new Audit("ated-subscription", auditConnector)
}

trait GovernmentGatewayAdminConnector extends RawResponseReads with Auditable {

  def serviceURL: String
  def addKnownFactsURI: String
  val http: HttpClient
  def metrics: ServiceMetrics

  def addKnownFacts(knownFacts: KnownFactsForService)(implicit headerCarrier: HeaderCarrier): Future[HttpResponse] = {

    val jsonData = Json.toJson(knownFacts)
    val baseUrl = s"""$serviceURL/government-gateway-admin/service"""
    val postUrl = s"""$baseUrl/${GovernmentGatewayConstants.AtedServiceName}/$addKnownFactsURI"""
    val timerContext = metrics.startTimer(MetricsEnum.GgAdminAddKnownFacts)
    http.POST[JsValue, HttpResponse](postUrl, jsonData) map {
      response =>
        val stopContext = timerContext.stop()
        auditAddKnownFactsCall(knownFacts, response)
        response.status match {
          case OK =>
            metrics.incrementSuccessCounter(MetricsEnum.GgAdminAddKnownFacts)
            response
          case status =>
            metrics.incrementFailedCounter(MetricsEnum.GgAdminAddKnownFacts)
            Logger.warn(s"[GovernmentGatewayAdminConnector][addKnownFacts] - status: $status")
            doFailedAudit("addKnownFactsFailed", jsonData.toString, response.body)
            response
        }
    }
  }

  private def auditAddKnownFactsCall(input: KnownFactsForService, response: HttpResponse)(implicit hc: HeaderCarrier): Unit = {
    val eventType = response.status match {
      case OK => EventTypes.Succeeded
      case _ => EventTypes.Failed
    }
    sendDataEvent(transactionName = "ggAddKnownFactsCall",
      detail = Map("txName" -> "ggAddKnownFactsCall",
        "facts" -> s"${input.facts}",
        "responseStatus" -> s"${response.status}",
        "responseBody" -> s"${response.body}",
        "status" -> s"$eventType"))
  }
}
