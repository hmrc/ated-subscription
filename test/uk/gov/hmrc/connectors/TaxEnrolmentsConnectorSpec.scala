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

package uk.gov.hmrc.connectors

import java.util.UUID

import builders.TestAudit
import connectors.TaxEnrolmentsConnector
import metrics.ServiceMetrics
import models.{Verifier, Verifiers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.JsValue
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.Future

class TaxEnrolmentsConnectorSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockHttp: HttpClient = mock[HttpClient]
  val mockServiceMetrics: ServiceMetrics = app.injector.instanceOf[ServiceMetrics]

  val mockServiceUrl = "tax-enrolments"

  trait Setup {
    class TestTaxEnrolmentsConnector extends TaxEnrolmentsConnector {
      val serviceUrl: String = mockServiceUrl
      val emacBaseUrl = s"$serviceUrl/tax-enrolments"
      override val audit: Audit = new TestAudit(app.injector.instanceOf[AuditConnector])
      override val metrics: ServiceMetrics = mockServiceMetrics
      override val http: HttpClient = mockHttp
    }

    val connector = new TestTaxEnrolmentsConnector
  }

  override def beforeEach: Unit = {
    reset(mockHttp)
  }

  val createVerifiers = Verifiers(List(Verifier("AtedReferenceNoType", "AtedReferenceNoType"),
    Verifier("PostalCode", "PostalCode"),
    Verifier("CTUTR", "CTUTR")))

  "TaxEnrolmentsConnector" must {

    "for successful set of known facts, return success" in new Setup {
      implicit val hc = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(mockHttp.PUT[JsValue, HttpResponse](any(),any(), any())(any(),any(),any(),any())).thenReturn(Future.successful(HttpResponse(NO_CONTENT)))
      val result: Future[HttpResponse] = connector.addKnownFacts(createVerifiers, "ATED-123")
      await(result).status must be(NO_CONTENT)
    }

    "for unsuccessful set of known facts, return subscription response" in new Setup {
      implicit val hc = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(mockHttp.PUT[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).thenReturn(Future.successful(HttpResponse(BAD_REQUEST)))
      val result: Future[HttpResponse] = connector.addKnownFacts(createVerifiers, "ATED-123")
      await(result).status must be(BAD_REQUEST)
    }

  }

}
