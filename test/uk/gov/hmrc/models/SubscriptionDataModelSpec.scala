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

package uk.gov.hmrc.models

import models.{Address, BusinessCustomerDetails, BusinessPartnerDetails}
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsObject, JsSuccess, Json}
import play.api.libs.json._
import uk.gov.hmrc.utils.TestJson

class SubscriptionDataModelSpec extends PlaySpec with TestJson {

  "EtmpRegistrationDetails" should {
    "be created successfully" when {

      "given valid Json" in {

        BusinessPartnerDetails.reads.reads(etmpWithRegimeOrgResponse) must be(JsSuccess(
          BusinessPartnerDetails(
            organisationName = Some("ACME Trading"),
            sapNumber = "1234567890",
            safeId = "XE0001234567890",
            regimeRefNumber = "XAAW00000123456",
            agentReferenceNumber = Some("AARN1234567")
          )
        ))
      }
    }

    "generate a runtime exception" when {

      "no regime reference number is provided in the enrolment identifiers block" in {

        val jsonTransformer = __.json.update(
          __.read[JsObject].map{ o => o ++ Json.obj( "regimeIdentifiers" -> "noIdentifier" ) }
        )

        val etmpWithRegimeOrgResponseWithoutRefNumber = etmpWithRegimeOrgResponse.transform(jsonTransformer).get

        val ex = intercept[RuntimeException](BusinessPartnerDetails.reads.reads(etmpWithRegimeOrgResponseWithoutRefNumber))

        ex.toString must be("java.lang.RuntimeException: [SubscriptionDataModel][BusinessPartnerDetails][reads] No ATED regime ref number")

      }
    }
  }

  "BusinessCustomerDetails" should {
    "generate correctly" when {
      "given valid JSON" in {

        val businessCustomerDetails = etmpCheckOrganisation.as[BusinessCustomerDetails]

        businessCustomerDetails must be(
          BusinessCustomerDetails(
            businessName = "ACME Limited",
            businessType = "Corporate Body",
            businessAddress = Address(
              "1 Example Street", "Example View", Some("Example Town"), Some("Exampleshire"), Some("AA1 1AA"), "GB"
            ),
            sapNumber = "1234567890",
            safeId = "XE0001234567890",
            None
          )
        )
      }
    }
  }
}
