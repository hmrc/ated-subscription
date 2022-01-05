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

package models

import play.api.libs.json._

case class BusinessPartnerDetails(
                                    organisationName: Option[String],
                                    sapNumber: String,
                                    safeId: String,
                                    regimeRefNumber: String,
                                    agentReferenceNumber: Option[String])

object BusinessPartnerDetails {
  val reads: Reads[BusinessPartnerDetails] = (js: JsValue) => {
    val regimeRefNumber: String = (js \ "regimeIdentifiers").asOpt[List[JsValue]].flatMap { regimeIdentifiers =>
      regimeIdentifiers.headOption.flatMap { regimeJs =>
        (regimeJs \ "regimeRefNumber").asOpt[String]
      }
    }.getOrElse(throw new RuntimeException("[SubscriptionDataModel][BusinessPartnerDetails][reads] No ATED regime ref number"))

    val organisationName = (js \ "organisation" \ "organisationName").asOpt[String]
    val sapNumber = (js \ "sapNumber").as[String]
    val safeId = (js \ "safeId").as[String]
    val agentReferenceNumber = (js \ "agentReferenceNumber").asOpt[String]

    JsSuccess(BusinessPartnerDetails(
      organisationName,
      sapNumber,
      safeId,
      regimeRefNumber,
      agentReferenceNumber
    ))
  }
}

case class Address(
                    line_1: String,
                    line_2: String,
                    line_3: Option[String] = None,
                    line_4: Option[String] = None,
                    postcode: Option[String] = None,
                    country: String
                  )

object Address {
  implicit val formats: OFormat[Address] = Json.format[Address]
}

case class BusinessCustomerDetails(
                                    businessName: String,
                                    businessType: String,
                                    businessAddress: Address,
                                    sapNumber: String,
                                    safeId: String,
                                    agentReferenceNumber: Option[String],
                                    utr: Option[String] = None)

object BusinessCustomerDetails {
  implicit val format: OFormat[BusinessCustomerDetails] = Json.format[BusinessCustomerDetails]
}
