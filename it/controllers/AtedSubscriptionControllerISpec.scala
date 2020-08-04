package controllers

import helpers.IntegrationSpec
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSResponse
import com.github.tomakehurst.wiremock.client.WireMock._

class AtedSubscriptionControllerISpec extends IntegrationSpec {

  val inputJson: JsValue = Json.parse(
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
      | "utr":"12345",
      | "businessType":"Corporate Body",
      | "isNonUKClientRegisteredByAgent": false,
      | "knownFactPostcode": "NE1 1EN"}
      |
    """.stripMargin
  )

  Map(
    "org"   -> "orgName",
    "agent" -> "agentName"
  ) foreach { case (userType, name) =>
    s"/$userType/$name/subscribe" should {
      "lookup business details" when {
        "business details are available" in {
          stubFor(post(urlMatching(s"/annual-tax-enveloped-dwellings/subscribe"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(
                  s"""{
                     |"atedRefNumber" : "ATEDREFNUMBER"
                     |}""".stripMargin
                )
            )
          )

          stubFor(put(urlMatching(s"/tax-enrolments/enrolments/HMRC-ATED-ORG~ATEDRefNumber~ATEDREFNUMBER"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(
                  s"""{
                     |"atedRefNumber" : "ATEDREFNUMBER"
                     |}""".stripMargin
                )
            )
          )

          val result: WSResponse = await(hitApplicationEndpoint(s"/$userType/$name/subscribe")
            .post(inputJson))

          result.status mustBe 200
        }
      }
    }
  }
}