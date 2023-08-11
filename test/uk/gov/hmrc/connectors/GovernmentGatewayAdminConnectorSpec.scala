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

package uk.gov.hmrc.connectors

import builders.{GGBuilder, TestAudit}
import connectors.GovernmentGatewayAdminConnector
import metrics.ServiceMetrics
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit

import java.util.UUID
import scala.concurrent.Future

class GovernmentGatewayAdminConnectorSpec extends PlaySpec with GuiceOneAppPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockWSHttp: HttpClient = mock[HttpClient]
  val mockServiceMetrics: ServiceMetrics = app.injector.instanceOf[ServiceMetrics]

  val mockServiceUrl = "government-gateway-admin"

  trait Setup {
    class TestGGAdminConnector extends GovernmentGatewayAdminConnector {
      override val serviceURL: String = mockServiceUrl
      override val addKnownFactsURI = "known-facts"
      override val http: HttpClient = mockWSHttp
      override val auditConnector: AuditConnector = app.injector.instanceOf[AuditConnector]
      override val audit: Audit = new TestAudit(auditConnector)
      override def metrics: ServiceMetrics = mockServiceMetrics
    }

    val connector = new TestGGAdminConnector()
  }

  override def beforeEach(): Unit = {
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

    "for successful set of known facts, return success" in new Setup {
      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(OK, succesfulSubscribeJson.toString)))

      val result: Future[HttpResponse] = connector.addKnownFacts(GGBuilder.createKnownFacts("ATED", "ATED-123"))
      await(result).status must be(OK)
    }

    "for unsuccessful set of known facts, return subscription response" in new Setup {
      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(BAD_REQUEST, unsuccessfulSubscribeJson.toString)))
      val result: Future[HttpResponse] = connector.addKnownFacts(GGBuilder.createKnownFacts("ATED", "ATED-123"))
      await(result).json must be(unsuccessfulSubscribeJson)
    }

  }
}
