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

package uk.gov.hmrc.services

import java.util.UUID

import connectors.{EtmpConnector, GovernmentGatewayAdminConnector, TaxEnrolmentsConnector}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import services.SubscribeService
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class SubscribeServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockEtmpConnector: EtmpConnector = mock[EtmpConnector]
  val mockggAdminConnector: GovernmentGatewayAdminConnector = mock[GovernmentGatewayAdminConnector]
  val mockTaxEnrolementConnector: TaxEnrolmentsConnector = mock[TaxEnrolmentsConnector]

  trait Setup {
    class TestSubscribeServiceSpecGG extends SubscribeService {
      override val ggAdminConnector = mockggAdminConnector
      override val etmpConnector = mockEtmpConnector
      override val taxEnrolmentsConnector: TaxEnrolmentsConnector = mockTaxEnrolementConnector
      override val isEmacFeatureToggle: Boolean = false
    }

    class TestSubscribeServiceSpecEMAC extends SubscribeService {
      override val ggAdminConnector = mockggAdminConnector
      override val etmpConnector = mockEtmpConnector
      override val taxEnrolmentsConnector: TaxEnrolmentsConnector = mockTaxEnrolementConnector
      override val isEmacFeatureToggle: Boolean = true
    }

    val subscribeServiceGG = new TestSubscribeServiceSpecGG
    val subscribeServiceEMAC = new TestSubscribeServiceSpecEMAC
  }

  override def beforeEach(): Unit = {
    reset(mockEtmpConnector)
  }

  val inputJson = Json.parse(
    """
      |{"acknowledgementReference":"Tp0x8ql6GldqGyGh6u36149378018603",
      |"safeId":"XE0001234567890",
      |"emailConsent":false,
      |"address":[
      | {
      |   "name1":"Paul",
      |    "name2":"Carrielies",
      |    "addressDetails": {
      |      "addressLine1": "100 SuttonStreet",
      |      "addressLine2": "Wokingham",
      |      "postalCode": "AB12CD",
      |      "countryCode": "GB"
      |    },
      |    "contactDetails": {
      |      "telephone": "01332752856",
      |      "mobile": "07782565326",
      |      "fax": "01332754256",
      |      "email": "aa@aa.com"
      |    }
      | }],
      | "businessType": "Corporate Body",
      | "utr":"12345",
      | "isNonUKClientRegisteredByAgent": false,
      | "knownFactPostcode": "NE1 1EN"}
      |
    """.stripMargin
  )

  "SubscribeService" must {

    val inputJsonNoUtrNoUKPostcode = Json.parse(
      """
        |{"acknowledgementReference":"Tp0x8ql6GldqGyGh6u36149378018603",
        |"safeId":"XE0001234567890",
        |"emailConsent":false,
        |"address":[
        | {
        |   "name1":"Paul",
        |    "name2":"Carrielies",
        |    "addressDetails": {
        |      "addressLine1": "100 SuttonStreet",
        |      "addressLine2": "Wokingham",
        |      "countryCode": "GB"
        |    },
        |    "contactDetails": {
        |      "telephone": "01332752856",
        |      "mobile": "07782565326",
        |      "fax": "01332754256",
        |      "email": "aa@aa.com"
        |    }
        | }],
        |  "isNonUKClientRegisteredByAgent": false,
        |  "knownFactPostcode": "12345678"}
        |
      """.stripMargin
    )

    val inputJsonNoPostcode = Json.parse(
      """
        |{"acknowledgementReference":"Tp0x8ql6GldqGyGh6u36149378018603",
        |"safeId":"XE0001234567890",
        |"emailConsent":false,
        |"address":[
        | {
        |   "name1":"Paul",
        |    "name2":"Carrielies",
        |    "addressDetails": {
        |      "addressLine1": "100 SuttonStreet",
        |      "addressLine2": "Wokingham",
        |      "countryCode": "GB"
        |    },
        |    "contactDetails": {
        |      "telephone": "01332752856",
        |      "mobile": "07782565326",
        |      "fax": "01332754256",
        |      "email": "aa@aa.com"
        |    }
        | }],
        |  "businessType": "LLP",
        |  "utr":"12345",
        |  "isNonUKClientRegisteredByAgent": false}
        |
      """.stripMargin
    )

    val inputJsonNoUtrNoPostCode = Json.parse(
      """
        |{"acknowledgementReference":"Tp0x8ql6GldqGyGh6u36149378018603",
        |"safeId":"XE0001234567890",
        |"emailConsent":false,
        |"address":[
        | {
        |   "name1":"Paul",
        |    "name2":"Carrielies",
        |    "addressDetails": {
        |      "addressLine1": "100 SuttonStreet",
        |      "addressLine2": "Wokingham",
        |      "countryCode": "GB"
        |    },
        |    "contactDetails": {
        |      "telephone": "01332752856",
        |      "mobile": "07782565326",
        |      "fax": "01332754256",
        |      "email": "aa@aa.com"
        |    }
        | }],
        |  "isNonUKClientRegisteredByAgent": false}
        |
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

    implicit val hc = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

    "subscribe when we are passed valid json adding known facts to GG" in new Setup {

      when(mockEtmpConnector.subscribeAted(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))
      when(mockggAdminConnector.addKnownFacts(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK)))
      val result = subscribeServiceGG.subscribe(inputJson)
      val response = await(result)
      response.status must be(OK)
      response.json must be(successResponse)
    }

    "subscribe when we are passed valid json doing upsert enrolment in EMAC" in new Setup {

      when(mockEtmpConnector.subscribeAted(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))
      when(mockTaxEnrolementConnector.addKnownFacts(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK)))
      val result = subscribeServiceEMAC.subscribe(inputJson)
      val response = await(result)
      response.status must be(OK)
      response.json must be(successResponse)
    }

    "subscribe when we are passed valid json doing upsert enrolment in EMAC with NO UTR and Non-UK Postcode" in new Setup {

      when(mockEtmpConnector.subscribeAted(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))
      when(mockTaxEnrolementConnector.addKnownFacts(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK)))
      val result = subscribeServiceEMAC.subscribe(inputJsonNoUtrNoUKPostcode)
      val response = await(result)
      response.status must be(OK)
      response.json must be(successResponse)
    }

    "throw exception when valid json with no utr and postcode is passeed for enrolment in EMAC" in new Setup {

      when(mockEtmpConnector.subscribeAted(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))
      val result = subscribeServiceEMAC.subscribe(inputJsonNoUtrNoPostCode)
      val thrown = the[RuntimeException] thrownBy await(result)
      thrown.getMessage must include("postalCode or utr must be supplied")
    }

    "respond with OK when only ctutr when valid json with no postcode is passed for enrolment in EMAC" in new Setup {
      when(mockEtmpConnector.subscribeAted(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))
      val result = subscribeServiceEMAC.subscribe(inputJsonNoPostcode)
      val response = await(result)
      response.status must be(OK)
      response.json must be(successResponse)
    }


    "subscribe without adding known facts if this is isNonUKClientRegisteredByAgent" in new Setup {
      val inputJsonNoKnownFacts = Json.parse(
        """
          |{"acknowledgementReference":"Tp0x8ql6GldqGyGh6u36149378018603",
          |"safeId":"XE0001234567890",
          |"emailConsent":false,
          |"address":[
          | {
          |   "name1":"Paul",
          |    "name2":"Carrielies",
          |    "addressDetails": {
          |      "addressLine1": "100 SuttonStreet",
          |      "addressLine2": "Wokingham",
          |      "countryCode": "GB"
          |    },
          |    "contactDetails": {
          |      "telephone": "01332752856",
          |      "mobile": "07782565326",
          |      "fax": "01332754256",
          |      "email": "aa@aa.com"
          |    }
          | }],
          | "isNonUKClientRegisteredByAgent": true}
          |
        """.stripMargin
      )

      when(mockEtmpConnector.subscribeAted(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))
      val result = subscribeServiceGG.subscribe(inputJsonNoKnownFacts)
      val response = await(result)
      response.status must be(OK)
      response.json must be(successResponse)
    }

    "throw exception when we are passed valid json with no utr and postcode" in new Setup {

      when(mockEtmpConnector.subscribeAted(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))
      val result = subscribeServiceGG.subscribe(inputJsonNoUtrNoPostCode)
      val thrown = the[RuntimeException] thrownBy await(result)
      thrown.getMessage must include("postalCode or utr must be supplied")
    }

    "throw exception when we are passed valid json with no ated ref" in new Setup {

      val successResponseNoAted = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z", "formBundleNumber": "123456789012345"}""")
      when(mockEtmpConnector.subscribeAted(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseNoAted))))
      when(mockggAdminConnector.addKnownFacts(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK)))
      val result = subscribeServiceGG.subscribe(inputJson)
      val thrown = the[RuntimeException] thrownBy await(result)
      thrown.getMessage must include("atedRefNumber not returned from etmp subscribe" )
    }


    "respond with BadRequest, when subscription request fails with a Bad request" in new Setup {
      when(mockEtmpConnector.subscribeAted(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, Some(failureResponse))))
      val result = subscribeServiceGG.subscribe(inputJson)
      val response = await(result)
      response.status must be(BAD_REQUEST)
      response.json must be(failureResponse)
    }

    "respond with an OK, when subscription works but gg admin request fails with a Bad request" in new Setup {
      when(mockEtmpConnector.subscribeAted(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))
      when(mockggAdminConnector.addKnownFacts(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, Some(failureResponse))))
      val result = subscribeServiceGG.subscribe(inputJson)
      val response = await(result)
      response.status must be(OK)
      response.json must be(successResponse)
    }
  }

  "stripUtr" must {
    val strippedJson = Json.parse(
      """
        |{"acknowledgementReference":"Tp0x8ql6GldqGyGh6u36149378018603",
        |"safeId":"XE0001234567890",
        |"emailConsent":false,
        |"address":[
        | {
        |   "name1":"Paul",
        |    "name2":"Carrielies",
        |    "addressDetails": {
        |      "addressLine1": "100 SuttonStreet",
        |      "addressLine2": "Wokingham",
        |      "postalCode": "AB12CD",
        |      "countryCode": "GB"
        |    },
        |    "contactDetails": {
        |      "telephone": "01332752856",
        |      "mobile": "07782565326",
        |      "fax": "01332754256",
        |      "email": "aa@aa.com"
        |    }
        | }]
        | }
        |
      """.stripMargin
    )

  }

}
