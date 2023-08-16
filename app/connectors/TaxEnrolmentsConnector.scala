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
import com.codahale.metrics.Timer

import javax.inject.Inject
import metrics.{MetricsEnum, ServiceMetrics}
import models._
import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import utils.GovernmentGatewayConstants

import scala.concurrent.{ExecutionContext, Future}

class DefaultTaxEnrolmentsConnector @Inject()(val servicesConfig: ServicesConfig,
                                              val auditConnector: AuditConnector,
                                              val metrics: ServiceMetrics,
                                              val http: HttpClient) extends TaxEnrolmentsConnector {
  val serviceUrl: String = servicesConfig.baseUrl("tax-enrolments")
  val enrolmentStoreProxyUrl: String = servicesConfig.baseUrl("enrolment-store-proxy")
  val emacBaseUrl = s"$serviceUrl/tax-enrolments/enrolments"
  override val audit: Audit = new Audit("ated-subscription", auditConnector)
}

trait TaxEnrolmentsConnector extends RawResponseReads with Auditable with Logging {

  def serviceUrl: String
  def enrolmentStoreProxyUrl: String
  def emacBaseUrl: String
  def metrics: ServiceMetrics
  def http: HttpClient

  def addKnownFacts(verifiers: Verifiers, atedRefNo: String)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {

    val atedRefIdentifier = "ATEDRefNumber"
    val enrolmentKey = s"${GovernmentGatewayConstants.AtedServiceName}~$atedRefIdentifier~$atedRefNo"
    val putUrl = s"$emacBaseUrl/$enrolmentKey"

    val timerContext: Timer.Context = metrics.startTimer(MetricsEnum.EmacAddKnownFacts)
    http.PUT[JsValue, HttpResponse](putUrl, Json.toJson(verifiers), Seq.empty) map { response =>
      timerContext.stop()
      auditAddKnownFacts(putUrl, verifiers, response)
      response.status match {
        case NO_CONTENT =>
          metrics.incrementSuccessCounter(MetricsEnum.EmacAddKnownFacts)
          response
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EmacAddKnownFacts)
          logger.warn(s"[TaxEnrolmentsConnector][addKnownFacts] - status: $status")
          doFailedAudit("addKnownFacts", verifiers.toString, response.body)
          response
      }
    }
  }

  private def auditAddKnownFacts(putUrl: String, verifiers: Verifiers, response: HttpResponse)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
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

  def getATEDUsers(atedRef: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[Int, AtedUsers]] = {

    val url = s"$enrolmentStoreProxyUrl/enrolment-store-proxy/enrolment-store/enrolments/HMRC-ATED-ORG~ATEDRefNumber~$atedRef/users"
    http.GET[HttpResponse](url, Seq.empty).map {
      response =>
        response.status match {
          case OK =>
            response.json.validate[AtedUsers].fold(_ => Left(INTERNAL_SERVER_ERROR), users => Right(users))
          case NO_CONTENT =>
            Right(AtedUsers(Nil, Nil))
          case BAD_REQUEST =>
            Left(BAD_REQUEST)
          case _ =>
            Left(response.status)
        }
    }
  }
}

