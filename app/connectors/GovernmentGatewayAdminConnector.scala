/*
 * Copyright 2017 HM Revenue & Customs
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
import models._
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.config.{AppName, ServicesConfig}
import utils.GovernmentGatewayConstants

import scala.concurrent.ExecutionContext.Implicits.global

trait GovernmentGatewayAdminConnector extends ServicesConfig with RawResponseReads with Auditable {

  def serviceURL: String

  def addKnownFactsURI: String

  val http: CoreGet with CorePost

  def metrics: Metrics

  def addKnownFacts(knownFacts: KnownFactsForService)(implicit headerCarrier: HeaderCarrier) = {

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



  private def auditAddKnownFactsCall(input: KnownFactsForService, response: HttpResponse)(implicit hc: HeaderCarrier) = {
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

object GovernmentGatewayAdminConnector extends GovernmentGatewayAdminConnector {
  val serviceURL = baseUrl("government-gateway-admin")
  val addKnownFactsURI = "known-facts"
  val audit: Audit = new Audit(AppName.appName, MicroserviceAuditConnector)
  val appName: String = AppName.appName
  val metrics = Metrics
  val http = WSHttp
}
