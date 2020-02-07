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

package models

import play.api.libs.json.{JsResult, JsSuccess, JsValue, Json, OFormat, Reads}

case class EtmpRegistrationDetails(
                                    organisationName: Option[String],
                                    sapNumber: String,
                                    safeId: String,
                                    isAGroup: Option[Boolean],
                                    regimeRefNumber: String,
                                    agentReferenceNumber: Option[String],
                                    firstName: Option[String] = None,
                                    lastName: Option[String] = None)

object EtmpRegistrationDetails {
  val etmpReader: Reads[EtmpRegistrationDetails] = new Reads[EtmpRegistrationDetails] {
    def reads(js: JsValue): JsResult[EtmpRegistrationDetails] = {
      val regimeRefNumber: String = (js \ "regimeIdentifiers").asOpt[List[JsValue]].flatMap { regimeIdentifiers =>
        regimeIdentifiers.headOption.flatMap { regimeJs =>
          (regimeJs \ "regimeRefNumber").asOpt[String]
        }
      }.getOrElse(throw new RuntimeException("[EtmpRegistrationDetails][etmpReader][reads] No regime ref number"))

      val organisationName = (js \ "organisation" \ "organisationName").asOpt[String]
      val sapNumber = (js \ "sapNumber").as[String]
      val safeId = (js \ "safeId").as[String]
      val isAGroup = (js \ "organisation" \ "isAGroup").asOpt[Boolean]
      val agentReferenceNumber = (js \ "agentReferenceNumber").asOpt[String]
      val firstName = (js \"individual" \ "firstName").asOpt[String]
      val lastName = (js \ "individual" \ "lastName").asOpt[String]

      JsSuccess(EtmpRegistrationDetails(
        organisationName,
        sapNumber,
        safeId,
        isAGroup,
        regimeRefNumber,
        agentReferenceNumber,
        firstName,
        lastName
      ))
    }
  }
}

case class Identification(idNumber: String, issuingInstitution: String, issuingCountryCode: String)

object Identification {
  implicit val formats: OFormat[Identification] = Json.format[Identification]
}

case class BusinessCustomerDetails(
                         businessName: String,
                         businessType: Option[String],
                         businessAddress: Address,
                         sapNumber: String,
                         safeId: String,
                         agentReferenceNumber: Option[String],
                         utr: Option[String] = None)

object BusinessCustomerDetails {
  implicit val formats: OFormat[BusinessCustomerDetails] = Json.format[BusinessCustomerDetails]
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
