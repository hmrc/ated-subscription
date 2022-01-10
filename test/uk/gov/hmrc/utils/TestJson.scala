/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.utils

import play.api.libs.json.{JsValue, Json}

trait TestJson {

  val etmpWithRegimeOrgResponse: JsValue = Json.parse(
    """
      |{
      |  "sapNumber": "1234567890",
      |  "safeId": "XE0001234567890",
      |  "agentReferenceNumber": "AARN1234567",
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
  )

  val etmpCheckOrganisation: JsValue = Json.parse(
    """{
      |    "businessName": "ACME Limited",
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

  val etmpCheckOrganisationInvalid: JsValue = Json.parse(
    """{
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
      |    "safeId": "XE0001234567890",
      |    "agentReferenceNumber": "JARN1234567"
      |}
      |""".stripMargin
  )

}
