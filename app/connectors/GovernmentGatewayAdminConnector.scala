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
import metrics.{MetricsEnum, ServiceMetrics}
import models._
import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import utils.GovernmentGatewayConstants
import uk.gov.hmrc.http.HttpReads.Implicits._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DefaultGovernmentGatewayAdminConnector @Inject()(val servicesConfig: ServicesConfig,
                                                       val auditConnector: AuditConnector,
                                                       val metrics: ServiceMetrics,
                                                       val http: HttpClientV2) extends GovernmentGatewayAdminConnector {
  val serviceURL: String = servicesConfig.baseUrl("government-gateway-admin")
  val addKnownFactsURI = "known-facts"
  override val audit: Audit = new Audit("ated-subscription", auditConnector)
}

trait GovernmentGatewayAdminConnector extends Auditable with Logging {

  def serviceURL: String
  val addKnownFactsURI: String
  val http: HttpClientV2
  def metrics: ServiceMetrics

  def addKnownFacts(knownFacts: KnownFactsForService)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {

    val jsonData = Json.toJson(knownFacts)
    val baseUrl = s"""$serviceURL/government-gateway-admin/service"""
    val postUrl = s"""$baseUrl/${GovernmentGatewayConstants.AtedServiceName}/$addKnownFactsURI"""
    val timerContext = metrics.startTimer(MetricsEnum.GgAdminAddKnownFacts)
    http.post(url"$postUrl")
      .withBody(jsonData)
      .execute[HttpResponse] map{
      response =>
        timerContext.stop()
        auditAddKnownFactsCall(knownFacts, response)
        response.status match {
          case OK =>
            metrics.incrementSuccessCounter(MetricsEnum.GgAdminAddKnownFacts)
            response
          case status =>
            metrics.incrementFailedCounter(MetricsEnum.GgAdminAddKnownFacts)
            logger.warn(s"[GovernmentGatewayAdminConnector][addKnownFacts] - status: $status")
            doFailedAudit("addKnownFactsFailed", jsonData.toString, response.body)
            response
        }
    }
  }

  private def auditAddKnownFactsCall(input: KnownFactsForService, response: HttpResponse)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
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