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

package uk.gov.hmrc.connectors

import java.util.UUID

import builders.{GGBuilder, TestAudit}
import connectors.GovernmentGatewayAdminConnector
import connectors.connectors.TaxEnrolmentsConnector
import metrics.Metrics
import models.{Verifier, Verifiers}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.play.config.{AppName, RunMode}
import uk.gov.hmrc.play.microservice.config.LoadAuditingConfig

import scala.concurrent.Future

class TaxEnrolmentsConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  object TestAuditConnector extends AuditConnector with AppName with RunMode {
    override lazy val auditingConfig = LoadAuditingConfig(s"$env.auditing")
  }

  trait MockedVerbs extends CorePut
  val mockWSHttp: CorePut = mock[MockedVerbs]

  object TestTaxEnrolmentsConnector extends TaxEnrolmentsConnector {
    val serviceUrl = baseUrl("enrolment-store-proxy")
    val emacBaseUrl = s"$serviceUrl/enrolment-store-proxy/enrolment-store/enrolments"
    override val audit: Audit = new TestAudit
    override val appName: String = "Test"
    override def metrics = Metrics
    override val http: CorePut = mockWSHttp
  }

  override def beforeEach = {
    reset(mockWSHttp)
  }
 val createVerifiers = Verifiers(List(Verifier("AtedReferenceNoType", "AtedReferenceNoType"),
         Verifier("PostalCode", "PostalCode"),
         Verifier("CTUTR", "CTUTR")))

  "TaxEnrolmentConnector" must {


    "use correct metrics" in {
      TaxEnrolmentsConnector.metrics must be(Metrics)
    }

    "for successful set of known facts, return success" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = TestTaxEnrolmentsConnector.addKnownFacts(createVerifiers, "ATED-123")
      await(result).status must be(NO_CONTENT)
    }

    "for unsuccessful set of known facts, return subscription response" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(BAD_REQUEST)))
      val result = TestTaxEnrolmentsConnector.addKnownFacts(createVerifiers, "ATED-123")
      await(result).status must be(BAD_REQUEST)
    }

  }

}
