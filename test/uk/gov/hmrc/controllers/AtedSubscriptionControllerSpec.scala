/*
 * Copyright 2019 HM Revenue & Customs
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
import controllers.AgentAtedSubscriptionController
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.SubscribeService

import scala.concurrent.Future
import uk.gov.hmrc.http.HttpResponse

class AtedSubscriptionControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockSubscribeService: SubscribeService = mock[SubscribeService]
  val orgId = "bwtiFLqlNp0baWPAavb7Jy-Klyg"

  object TestAtedSubscriptionController extends AtedSubscriptionController {
    override val subscribeService: SubscribeService = mockSubscribeService
  }

  override def beforeEach = {
    reset(mockSubscribeService)
  }

  val inputJson = Json.parse(
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

  val successResponse = Json.parse(
    """
      |{
      |  "processingDate": "2001-12-17T09:30:47Z",
      |  "atedRefNumber": "ABCDEabcde12345",
      |  "formBundleNumber": "123456789012345"
      |}
    """.stripMargin
  )

  val failureResponse = Json.parse(
    """
      |{
      |  "Reason": "Your submission contains one or more errors."
      |}
    """.stripMargin
  )

  "AtedSubscriptionController" must {
    "use correct SubscribeService" in {
      AtedSubscriptionController.subscribeService must be(SubscribeService)
      AgentAtedSubscriptionController.subscribeService must be(SubscribeService)
    }

    "subscribe" must {
      "response with OK, when subscription request was successful" in {
        when(mockSubscribeService.subscribe(Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))
        val result = TestAtedSubscriptionController.subscribe(orgId).apply(FakeRequest().withJsonBody(inputJson))
        status(result) must be(OK)
      }
      "response with BadRequest, when subscription request was containing bad data" in {
        when(mockSubscribeService.subscribe(Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, Some(failureResponse))))
        val result = TestAtedSubscriptionController.subscribe(orgId).apply(FakeRequest().withJsonBody(inputJson))
        status(result) must be(BAD_REQUEST)
      }
      "response with NotFound, when The remote endpoint has indicated that no data can be found" in {
        when(mockSubscribeService.subscribe(Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, Some(failureResponse))))
        val result = TestAtedSubscriptionController.subscribe(orgId).apply(FakeRequest().withJsonBody(inputJson))
        status(result) must be(NOT_FOUND)
      }
      "response with ServiceUnavailable, when ETMP is not responding or has returned a HTTP 500" in {
        when(mockSubscribeService.subscribe(Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, Some(failureResponse))))
        val result = TestAtedSubscriptionController.subscribe(orgId).apply(FakeRequest().withJsonBody(inputJson))
        status(result) must be(SERVICE_UNAVAILABLE)
      }
      "response with Internal Server error, when any other status is returned" in {
        when(mockSubscribeService.subscribe(Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(GATEWAY_TIMEOUT, Some(failureResponse))))
        val result = TestAtedSubscriptionController.subscribe(orgId).apply(FakeRequest().withJsonBody(inputJson))
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }
  }

}
