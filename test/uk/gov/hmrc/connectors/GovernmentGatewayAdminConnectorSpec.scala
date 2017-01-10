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

package uk.gov.hmrc.connectors

import java.util.UUID

import builders.{GGBuilder, TestAudit}
import connectors.GovernmentGatewayAdminConnector
import metrics.Metrics
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.play.audit.http.config.LoadAuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.play.config.{AppName, RunMode}
import uk.gov.hmrc.play.http.logging.SessionId
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost, HttpResponse}

import scala.concurrent.Future

class GovernmentGatewayAdminConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  object TestAuditConnector extends AuditConnector with AppName with RunMode {
    override lazy val auditingConfig = LoadAuditingConfig(s"$env.auditing")
  }

  class MockHttp extends WSGet with WSPost {
    override val hooks = NoneRequired
  }

  val mockWSHttp = mock[MockHttp]

  object TestGGAdminConnector extends GovernmentGatewayAdminConnector {
    override val serviceURL = baseUrl("government-gateway-admin")
    override val addKnownFactsURI = "known-facts"
    override val http: HttpGet with HttpPost = mockWSHttp
    override val audit: Audit = new TestAudit
    override val appName: String = "Test"

    override def metrics = Metrics
  }

  override def beforeEach = {
    reset(mockWSHttp)
  }

  "GovernmentGatewayAdminConnector" must {

    val succesfulSubscribeJson = Json.parse(
      """
        |{
        |  "processingDate": "2001-12-17T09:30:47Z",
        |  "atedRefNumber": "ABCDEabcde12345",
        |  "formBundleNumber": "123456789012345"
        |}
      """.stripMargin
    )

    val unsuccessfulSubscribeJson = Json.parse( """{ "Reason": "Your submission contains one or more errors." }""")

    "use correct metrics" in {
      GovernmentGatewayAdminConnector.metrics must be(Metrics)
    }

    "for successful set of known facts, return success" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(succesfulSubscribeJson))))

      val result = TestGGAdminConnector.addKnownFacts(GGBuilder.createKnownFacts("ATED", "ATED-123"))
      await(result).status must be(OK)
    }

    "for unsuccessful set of known facts, return subscription response" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(BAD_REQUEST, responseJson = Some(unsuccessfulSubscribeJson))))
      val result = TestGGAdminConnector.addKnownFacts(GGBuilder.createKnownFacts("ATED", "ATED-123"))
      await(result).json must be(unsuccessfulSubscribeJson)
    }

  }

}
