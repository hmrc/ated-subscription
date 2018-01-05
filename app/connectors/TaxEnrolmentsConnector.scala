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

  def addKnownFacts(knownFacts: KnownFactsForService, atedRefNo: String)(implicit headerCarrier: HeaderCarrier) = {

    val atedRefIdentifier = "ATEDRefNumber"
    val enrolmentKey = s"${GovernmentGatewayConstants.AtedServiceName}~$atedRefIdentifier~$atedRefNo"
    val putUrl = s"$emacBaseUrl/$enrolmentKey"

    val timerContext = metrics.startTimer(MetricsEnum.EmacAddKnownFacts)
    http.PUT[JsValue, HttpResponse](putUrl, Json.toJson(knownFacts)) map { response =>
      timerContext.stop()
      auditAddKnownFacts(GovernmentGatewayConstants.AtedServiceName, knownFacts, response)
      response.status match {
        case NO_CONTENT =>
          metrics.incrementSuccessCounter(MetricsEnum.EmacAddKnownFacts)
          response
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EmacAddKnownFacts)
          Logger.warn(s"[TaxEnrolmentsConnector][addKnownFacts] - status: $status")
          doFailedAudit("addKnownFacts", knownFacts.toString, response.body)
          response
      }
    }
  }

  private def auditAddKnownFacts(serviceName: String, knownFacts: KnownFactsForService, response: HttpResponse)(implicit hc: HeaderCarrier) = {
    val status = response.status match {
      case NO_CONTENT => EventTypes.Succeeded
      case _ => EventTypes.Failed
    }
    sendDataEvent(transactionName = "emacAddKnownFacts",
      detail = Map("txName" -> "emacAddKnownFacts",
        "serviceName" -> s"$serviceName",
        "knownFacts" -> s"$knownFacts",
        "responseStatus" -> s"${response.status}",
        "responseBody" -> s"${response.body}",
        "status" -> s"$status"))
  }
}

object TaxEnrolmentsConnector extends TaxEnrolmentsConnector {
  val serviceUrl = baseUrl("enrolment-store-proxy")
  val emacBaseUrl = s"$serviceUrl/enrolment-store-proxy/enrolment-store/enrolments"
  val metrics = Metrics
  val http = WSHttp
  val audit: Audit = new Audit(AppName.appName, MicroserviceAuditConnector)
  val appName: String = AppName.appName
}
