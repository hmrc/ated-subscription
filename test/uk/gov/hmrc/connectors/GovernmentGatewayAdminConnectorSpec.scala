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

import builders.GGBuilder
import connectors.{DefaultGovernmentGatewayAdminConnector, GovernmentGatewayAdminConnector}
import metrics.ServiceMetrics
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GovernmentGatewayAdminConnectorSpec extends PlaySpec with ConnectorTest with GuiceOneAppPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockServiceMetrics: ServiceMetrics = app.injector.instanceOf[ServiceMetrics]
  val auditConnector: AuditConnector = app.injector.instanceOf[AuditConnector]
  val servicesConfig: ServicesConfig = app.injector.instanceOf[ServicesConfig]

  class Setup extends ConnectorTest{
    val testAtedConnector: GovernmentGatewayAdminConnector = new DefaultGovernmentGatewayAdminConnector(servicesConfig, auditConnector, mockServiceMetrics, mockHttpClient)
  }


  override def beforeEach(): Unit = {
    reset(mockHttpClient)
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
      when(requestBuilder.execute[HttpResponse](any, any)).thenReturn(Future.successful(HttpResponse(OK, succesfulSubscribeJson.toString)))
      val result: Future[HttpResponse] = testAtedConnector.addKnownFacts(GGBuilder.createKnownFacts("ATED", "ATED-123"))
      await(result).status must be(OK)
    }

    "for unsuccessful set of known facts, return subscription response" in new Setup {
      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(requestBuilder.execute[HttpResponse](any, any)).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, unsuccessfulSubscribeJson.toString)))

      val result: Future[HttpResponse] = testAtedConnector.addKnownFacts(GGBuilder.createKnownFacts("ATED", "ATED-123"))
      await(result).json must be(unsuccessfulSubscribeJson)
    }
  }
}