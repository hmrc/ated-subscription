package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import helpers.{IntegrationSpec, LoginStub}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSResponse
import play.api.http.Status.{NO_CONTENT, OK}

class EtmpCheckControllerISpec extends IntegrationSpec with LoginStub {

  val inputJson: JsValue = Json.parse(
    """{
      |    "businessName": "ACME Trading",
      |    "businessType": "Corporate Body",
      |    "businessAddress": {
      |      "line_1": "1 Example Street",
      |      "line_2": "Example View",
      |      "line_3": "Example Town",
      |      "line_4": "Exampleshire",
      |      "postcode": "AA1 1AA",
      |      "country": "GB"
      |    },
      |    "sapNumber": "1234567890",
      |    "safeId": "XE0001234567890"
      |  }
      |""".stripMargin
  )

  "/regime-etmp-check" should {
    "subscribe the user for ated" when {
      "business details are available in ETMP" in {

        val jsResultString =
          """
            |{
            |  "sapNumber": "1234567890",
            |  "safeId": "XE0001234567890",
            |  "regimeIdentifiers": [
            |    {
            |      "regimeName": "ATED",
            |      "regimeRefNumber": "XAAW00000123456"
            |    },
            |    {
            |      "regimeRefNumber": "XAML00000123456"
            |    }
            |  ],
            |  "nonUKIdentification": {
            |    "idNumber": "123456",
            |    "issuingInstitution": "France Institution",
            |    "issuingCountryCode": "FR"
            |  },
            |  "isEditable": true,
            |  "isAnAgent": false,
            |  "isAnIndividual": false,
            |  "organisation": {
            |    "organisationName": "ACME Trading",
            |    "isAGroup": false,
            |    "organisationType": "Corporate body"
            |  },
            |  "addressDetails": {
            |    "addressLine1": "100 SomeStreet",
            |    "addressLine2": "Wokingham",
            |    "addressLine3": "Surrey",
            |    "addressLine4": "London",
            |    "postalCode": "DH14EJ",
            |    "countryCode": "GB"
            |  },
            |  "contactDetails": {
            |    "regimeName": "ATED",
            |    "phoneNumber": "01332752856",
            |    "mobileNumber": "07782565326",
            |    "faxNumber": "01332754256",
            |    "emailAddress": "stephen@manncorpone.co.uk"
            |  }
            |}
            |
    """.stripMargin

        stubFor(get(urlMatching("/registration/details\\?safeid=XE0001234567890&regime=ATED"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(jsResultString)
          )
        )

        stubFor(post(urlMatching("/auth/authorise"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                """
                  |{
                  | "credentialRole":"User",
                  | "affinityGroup": "Organisation"
                  |}
                """.stripMargin)
          )
        )

        stubFor(put(urlMatching("/tax-enrolments/enrolments/" +
          "HMRC-ATED-ORG~ATEDRefNumber~XAAW00000123456"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
              .withBody(
                s"""{
                   |"atedRefNumber" : "ATEDREFNUMBER"
                   |}""".stripMargin
              )
          )
        )

        val result: WSResponse = await(hitApplicationEndpoint("/regime-etmp-check")
          .post(inputJson))

        result.status mustBe OK
      }
    }

    "not subscribe the user for ated" when {
      "business details are not available in ETMP" in {

        stubFor(get(urlMatching("/registration/details\\?safeid=XE0001234567890&regime=ATED"))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
        )

        stubFor(post(urlMatching("/auth/authorise"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                """
                  |{
                  | "credentialRole":"User",
                  | "affinityGroup": "Organisation"
                  |}
                """.stripMargin)
          )
        )

        val result: WSResponse = await(hitApplicationEndpoint("/regime-etmp-check")
          .post(inputJson))

        result.status mustBe NO_CONTENT
      }
    }
  }
}
