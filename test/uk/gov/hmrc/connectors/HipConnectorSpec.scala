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

import connectors.{DefaultHipConnector, HipConnector}
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
import uk.gov.hmrc.utils.TestJson
import utils.FeatureSwitch

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HipConnectorSpec extends PlaySpec with ConnectorTest with GuiceOneAppPerSuite with MockitoSugar with BeforeAndAfterEach with TestJson {

  val mockServiceMetrics: ServiceMetrics = app.injector.instanceOf[ServiceMetrics]
  val auditConnector: AuditConnector = app.injector.instanceOf[AuditConnector]
  implicit val servicesConfig: ServicesConfig = app.injector.instanceOf[ServicesConfig]

  class Setup extends ConnectorTest {
    val testHipConnector: HipConnector = new DefaultHipConnector(servicesConfig, auditConnector, mockServiceMetrics, mockHttpClient)
  }

  "HipConnector" must {

    val successfulSubscribeJson = Json.parse(
      """
        |{
        |  "processingDate": "2001-12-17T09:30:47Z",
        |  "atedRefNumber": "ABCDEabcde12345",
        |  "formBundleNumber": "123456789012345"
        |}
      """.stripMargin
    )

    val wrappedSuccessfulSubscribeJson = Json.obj("success" -> successfulSubscribeJson)

    val unsuccessfulSubscribeJson = Json.parse(
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
      FeatureSwitch.enable(FeatureSwitch("hipSwitch", true))
      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(CREATED, wrappedSuccessfulSubscribeJson.toString)))

      val result: Future[HttpResponse] = testHipConnector.subscribeAted(inputJson)
      await(result).json must be(successfulSubscribeJson)
    }

    "for successful subscription, return subscription response and audit international address" in new Setup {
      FeatureSwitch.enable(FeatureSwitch("hipSwitch", true))
      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(requestBuilder.execute[HttpResponse](any, any)).thenReturn(Future.successful(HttpResponse(CREATED, wrappedSuccessfulSubscribeJson.toString)))

      val result: Future[HttpResponse] = testHipConnector.subscribeAted(inputJsonNoPostcode)
      await(result).json must be(successfulSubscribeJson)
    }

    "for unsuccessful subscription, return subscription response" in new Setup {
      FeatureSwitch.enable(FeatureSwitch("hipSwitch", true))
      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(requestBuilder.execute[HttpResponse](any, any)).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, unsuccessfulSubscribeJson.toString)))

      val result: Future[HttpResponse] = testHipConnector.subscribeAted(inputJson)
      await(result).json must be(unsuccessfulSubscribeJson)
    }

    "submit data with unprocessable entity response converted to relevant status" in new Setup {

      val scenarios = List(
        ("001", BAD_REQUEST),
        ("002", BAD_REQUEST),
        ("003", BAD_REQUEST),
        ("999", INTERNAL_SERVER_ERROR)
      )

      scenarios.foreach{ case (code, status) =>
        val unprocessableResponse = Json.parse(
          s"""{
             |  "errors": {
             |    "processingDate": "2025-12-09T12:34:46.672Z",
             |    "code": "$code",
             |    "text": "ID not found"
             |  }
             |}
             |""".stripMargin)

        implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(UNPROCESSABLE_ENTITY, unprocessableResponse, Map.empty[String, Seq[String]])))
        val result: Future[HttpResponse] = testHipConnector.subscribeAted(inputJson)
        val response = await(result)
        response.status must be(status)
      }
    }
  }
}