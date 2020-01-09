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
import connectors.ETMPConnector
import metrics.ServiceMetrics
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.Future

class ETMPConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockWSHttp: HttpClient = mock[HttpClient]
  val mockServiceMetrics: ServiceMetrics = app.injector.instanceOf[ServiceMetrics]

  val mockServiceUrl = "etmp-hod"

  trait Setup {
    class TestETMPConnector extends ETMPConnector {
      override val serviceURL: String = mockServiceUrl
      override val baseURI = "annual-tax-enveloped-dwellings"
      override val subscribeUri = "subscribe"
      override val http: HttpClient = mockWSHttp
      override val urlHeaderEnvironment: String = ""
      override val urlHeaderAuthorization: String = ""
      override val audit: Audit = new TestAudit(app.injector.instanceOf[AuditConnector])

      override def metrics: ServiceMetrics = mockServiceMetrics
    }

    val connector: TestETMPConnector = new TestETMPConnector
  }

  override def beforeEach = {
    reset(mockWSHttp)
  }

  "DesConnector" must {

    val succesfulSubscribeJson = Json.parse(
      """
        |{
        |  "processingDate": "2001-12-17T09:30:47Z",
        |  "atedRefNumber": "ABCDEabcde12345",
        |  "formBundleNumber": "123456789012345"
        |}
      """.stripMargin
    )

    val unsuccesfulSubscribeJson = Json.parse(
      """
        |{
        |  "Reason": "Your submission contains one or more errors."
        |}
      """.stripMargin
    )

    val inputJson = Json.parse(
      """
        |{
        |  "safeId": "XE0001234567890",
        |  "address": {
        |    "addressLine1": "address-line-1",
        |    "addressLine2": "address-line-2",
        |    "postalCode": "AB12CD",
        |    "countryCode": "GB"
        |  },
        |  "contactDetails": {
        |    "telephone": "0123456789",
        |    "mobile": "0123456789",
        |    "fax": "0123456789",
        |    "email": "aa@aa.com"
        |  }
        |}
      """.stripMargin
    )

    val inputJsonNoPostcode = Json.parse(
      """
        |{
        |  "safeId": "XE0001234567890",
        |  "address": {
        |    "addressLine1": "address-line-1",
        |    "addressLine2": "address-line-2",
        |    "countryCode": "GB"
        |  },
        |  "contactDetails": {
        |    "telephone": "0123456789",
        |    "mobile": "0123456789",
        |    "fax": "0123456789",
        |    "email": "aa@aa.com"
        |  }
        |}
      """.stripMargin
    )

    "for successful subscription, return subscription response" in new Setup {
      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(mockWSHttp.POST[JsValue, HttpResponse]
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(succesfulSubscribeJson))))
      val result: Future[HttpResponse] = connector.subscribeAted(inputJson)
      await(result).json must be(succesfulSubscribeJson)
    }

    "for successful subscription, return subscription response and uadit internation address" in new Setup {
      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(mockWSHttp.POST[JsValue, HttpResponse]
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(succesfulSubscribeJson))))
      val result: Future[HttpResponse] = connector.subscribeAted(inputJsonNoPostcode)
      await(result).json must be(succesfulSubscribeJson)
    }

    "for unsuccessful subscription, return subscription response" in new Setup {
      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(mockWSHttp.POST[JsValue, HttpResponse]
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, responseJson = Some(unsuccesfulSubscribeJson))))
      val result: Future[HttpResponse] = connector.subscribeAted(inputJson)
      await(result).json must be(unsuccesfulSubscribeJson)
    }

  }

}
