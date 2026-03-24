/*
 * Copyright 2024 HM Revenue & Customs
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

package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import helpers.IntegrationSpec
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import utils.FeatureSwitch

class HipAtedSubscriptionControllerISpec extends IntegrationSpec {

  implicit val servicesConfig: ServicesConfig = app.injector.instanceOf[ServicesConfig]

  override def beforeAll(): Unit = {
    super.beforeAll()
    FeatureSwitch.enable(FeatureSwitch("hipSwitch", true))
  }

  override def afterAll(): Unit = {
    super.afterAll()
    FeatureSwitch.disable(FeatureSwitch("hipSwitch", false))
  }

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
          stubFor(post(urlMatching("/etmp/RESTAdapter/ated/subscription"))

            .willReturn(
              aResponse()
                .withStatus(201)
                .withBody(
                  s"""{
                     |  "success":
                     |  {
                     |    "atedRefNumber" : "ATEDREFNUMBER"
                     |  }
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