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

package uk.gov.hmrc.controllers

import controllers.AtedSubscriptionController
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.SubscribeService
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class AtedSubscriptionControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockSubscribeService: SubscribeService = mock[SubscribeService]
  val orgId = "bwtiFLqlNp0baWPAavb7Jy-Klyg"

  trait Setup {
    val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]

    class TestAtedSubscriptionController extends BackendController(cc) with AtedSubscriptionController {
      override val subscribeService: SubscribeService = mockSubscribeService
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    }

    val controller = new TestAtedSubscriptionController()
  }

  override def beforeEach(): Unit = {
    reset(mockSubscribeService)
  }

  val inputJson: JsValue = Json.parse(
    """
      |{
      |  "safeId": "XE0001234567890",
      |  "address": {
      |    "addressLine1": "100 SuttonStreet",
      |    "addressLine2": "Wokingham",
      |    "postalCode": "AB12CD",
      |    "countryCode": "GB"
      |  },
      |  "contactDetails": {
      |    "telephone": "01332752856",
      |    "mobile": "07782565326",
      |    "fax": "01332754256",
      |    "email": "aa@aa.com"
      |  }
      |}
    """.stripMargin
  )

  val successResponse: JsValue = Json.parse(
    """
      |{
      |  "processingDate": "2001-12-17T09:30:47Z",
      |  "atedRefNumber": "ABCDEabcde12345",
      |  "formBundleNumber": "123456789012345"
      |}
    """.stripMargin
  )

  val failureResponse: JsValue = Json.parse(
    """
      |{
      |  "Reason": "Your submission contains one or more errors."
      |}
    """.stripMargin
  )

  "AtedSubscriptionController" must {
    "subscribe" must {
      "response with OK, when subscription request was successful" in new Setup {
        when(mockSubscribeService.subscribe(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse.apply(OK, successResponse.toString)))
        val result: Future[Result] = controller.subscribe(orgId).apply(FakeRequest().withJsonBody(inputJson))
        status(result) must be(OK)
      }
      "response with BadRequest, when subscription request was containing bad data" in new Setup {
        when(mockSubscribeService.subscribe(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse.apply(BAD_REQUEST, failureResponse.toString)))
        val result: Future[Result] = controller.subscribe(orgId).apply(FakeRequest().withJsonBody(inputJson))
        status(result) must be(BAD_REQUEST)
      }
      "response with NotFound, when The remote endpoint has indicated that no data can be found" in new Setup {
        when(mockSubscribeService.subscribe(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse.apply(NOT_FOUND, failureResponse.toString)))
        val result: Future[Result] = controller.subscribe(orgId).apply(FakeRequest().withJsonBody(inputJson))
        status(result) must be(NOT_FOUND)
      }
      "response with ServiceUnavailable, when ETMP is not responding or has returned a HTTP 500" in new Setup {
        when(mockSubscribeService.subscribe(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse.apply(SERVICE_UNAVAILABLE, failureResponse.toString)))
        val result: Future[Result] = controller.subscribe(orgId).apply(FakeRequest().withJsonBody(inputJson))
        status(result) must be(SERVICE_UNAVAILABLE)
      }
      "response with Internal Server error, when any other status is returned" in new Setup {
        when(mockSubscribeService.subscribe(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse.apply(GATEWAY_TIMEOUT, failureResponse.toString)))
        val result: Future[Result] = controller.subscribe(orgId).apply(FakeRequest().withJsonBody(inputJson))
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }
  }

}
