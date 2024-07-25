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

package uk.gov.hmrc.services

import connectors.{EtmpConnector, TaxEnrolmentsConnector}
import models.{Address, BusinessCustomerDetails, BusinessPartnerDetails}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsObject
import play.api.test.Helpers._
import services.{EtmpRegimeService, SubscribeService}
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, SessionId}
import uk.gov.hmrc.utils.TestJson
import utils.BusinessTypeConstants

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class EtmpRegimeServiceSpec extends PlaySpec with MockitoSugar with TestJson with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val mockEtmpConnector: EtmpConnector = mock[EtmpConnector]
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockSubscribeService: SubscribeService = mock[SubscribeService]
  val mockTaxEnrolments: TaxEnrolmentsConnector = mock[TaxEnrolmentsConnector]
  val safeId: String = "XE0001234567890"
  val sapNumber: String = "1234567890"
  val businessName: String = "ACME Trading"
  val agentRef: String = "AARN1234567"

  val businessAddress: Address = Address(
    "1 LS House", "LS Way", Some("LS"), Some("Line 4"), Some("Postcode"), "GB")

  val businessCustomerDetails: BusinessCustomerDetails = BusinessCustomerDetails(
    businessName = businessName,
    BusinessTypeConstants.unitTrust,
    businessAddress = businessAddress,
    sapNumber = sapNumber,
    safeId = safeId,
    agentReferenceNumber = Some(agentRef),
    None
  )

  val etmpRegistrationDetails: BusinessPartnerDetails = BusinessPartnerDetails(
    organisationName = Some(businessName),
    sapNumber = sapNumber,
    safeId = safeId,
    regimeRefNumber = "XAAW00000123456",
    agentReferenceNumber = Some(agentRef)
  )

  override def beforeEach(): Unit = {
    reset(mockEtmpConnector)
    reset(mockTaxEnrolments)
    reset(mockAuthConnector)
    super.beforeEach()
  }

  object TestEtmpRegimeService extends EtmpRegimeService(mockEtmpConnector, mockSubscribeService, mockTaxEnrolments, mockAuthConnector)

  "getEtmpBusinessDetails" should {

    "successfully return a regimeRefNumber" in {
      when(mockEtmpConnector.atedRegime(ArgumentMatchers.eq(safeId))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse.apply(OK, etmpWithRegimeOrgResponse.toString)))

      val result = TestEtmpRegimeService.getEtmpBusinessDetails(safeId)

      await(result) must be(Some(etmpRegistrationDetails))
    }

    "successfully return an empty ref when no regime ref number is present" in {
      val json: JsObject = etmpWithRegimeOrgResponse.as[JsObject].-("regimeIdentifiers")

      when(mockEtmpConnector.atedRegime(ArgumentMatchers.eq(safeId))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse.apply(OK, json.toString())))

      val result = TestEtmpRegimeService.getEtmpBusinessDetails(safeId)

      await(result) must be(None)
    }
  }

  "checkEtmpBusinessPartnerExists" should {

    "successfully return an ETMP registration" in {
      when(mockAuthConnector.authorise[Option[AffinityGroup]](ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(AffinityGroup.Organisation)))
      when(mockEtmpConnector.atedRegime(ArgumentMatchers.eq(safeId))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse.apply(OK, etmpWithRegimeOrgResponse.toString)))
      when(mockTaxEnrolments.addKnownFacts(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse.apply(NO_CONTENT, "")))

      val result = TestEtmpRegimeService.checkEtmpBusinessPartnerExists(safeId, businessCustomerDetails)

      await(result) must be(Some(etmpRegistrationDetails))
    }


    "fail to return an ETMP registration when there are no regimeIdentifiers" in {
      when(mockEtmpConnector.atedRegime(ArgumentMatchers.eq(safeId))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse.apply(OK, "")))

      val result = TestEtmpRegimeService.checkEtmpBusinessPartnerExists(safeId, businessCustomerDetails)

      await(result) must be(None)
    }

    "fail to return if the ETMP call throws an exception" in {
      when(mockEtmpConnector.atedRegime(ArgumentMatchers.eq(safeId))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new RuntimeException("test")))

      val result = TestEtmpRegimeService.checkEtmpBusinessPartnerExists(safeId, businessCustomerDetails)

      await(result) must be(None)
    }

    "fail to return an ETMP registration when upserting enrolment fails" in {
      when(mockAuthConnector.authorise[Option[AffinityGroup]](ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(AffinityGroup.Organisation)))
      when(mockEtmpConnector.atedRegime(ArgumentMatchers.eq(safeId))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse.apply(OK, etmpWithRegimeOrgResponse.toString)))
      when(mockTaxEnrolments.addKnownFacts(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new RuntimeException("failure to return registration details")))

      val result = TestEtmpRegimeService.checkEtmpBusinessPartnerExists(safeId, businessCustomerDetails)

      await(result) must be(None)
    }
  }

  "checkAffinityAgainstEtmpDetails" should {
    "return ETMP registration details for an organisation" when {

      "the regimeRefNumber in ETMP matches the one in business matching" in {
        when(mockAuthConnector.authorise[Option[AffinityGroup]](ArgumentMatchers.any(),
          ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(AffinityGroup.Organisation)))

        val result = TestEtmpRegimeService.checkAffinityAgainstEtmpDetails(
          etmpRegistrationDetails, businessCustomerDetails
        )

        await(result) must be(Some(etmpRegistrationDetails))
      }
    }

    "return none" when {

      "ETMP does not match business details" in {
        when(mockAuthConnector.authorise[Option[AffinityGroup]](ArgumentMatchers.any(),
          ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(AffinityGroup.Organisation)))

        val etmpRegistrationDetails =
          BusinessPartnerDetails(
            Some("ACME Trading"), "1", "XE0001234567890","XAAW00000123456", Some("AARN1234567")
          )

        val result = TestEtmpRegimeService
          .checkAffinityAgainstEtmpDetails(etmpRegistrationDetails, businessCustomerDetails)

        await(result) must be(None)
      }
    }

    "compareAffinityAgainstRegDetails returns Some EtmpRegistrationDetails" when {

      "passed affinity group organisation" in {
        val result = TestEtmpRegimeService.compareAffinityAgainstRegDetails(Some(AffinityGroup.Organisation), businessCustomerDetails, etmpRegistrationDetails)

        result must be(Some(etmpRegistrationDetails))
      }
    }

    "compareAffinityAgainstRegDetails return None" when {

      "passed other affinity group" in {
        val result = TestEtmpRegimeService.compareAffinityAgainstRegDetails(Some(AffinityGroup.Agent), businessCustomerDetails, etmpRegistrationDetails)

        result must be(None)
      }
    }
  }

  def makeBusinessCustomerDetails(businessName: String, sapNumber: String, safeId: String,
                                  isAGroup: Boolean, agentRefNumber: Option[String]): BusinessCustomerDetails =
    BusinessCustomerDetails(
      businessName,
      BusinessTypeConstants.limitedPartnership,
      Address("1 LS House", "LS Way", Some("LS"), Some("Line 4"), Some("Postcode"), "GB"),
      sapNumber,
      safeId,
      agentRefNumber
    )

  def makeEtmpDetails(businessName: String, sapNumber: String, safeId: String, isAGroup: Boolean,
                      regimeRefNumber: String, agentRefNumber: Option[String],
                      firstName: Option[String], lastName: Option[String]): BusinessPartnerDetails =
    BusinessPartnerDetails(
      Some(businessName),
      sapNumber,
      safeId,
      regimeRefNumber,
      agentRefNumber
    )

  "matchOrg" should {
    "return true" when {

      "all elements match" in {
        val bcd = makeBusinessCustomerDetails("businessName", "sapNumber", "safeId", isAGroup = true, Some("agentRef"))
        val ecd = makeEtmpDetails("businessName", "sapNumber", "safeId", isAGroup = true, "regimeRef", Some("agentRef"), Some("first"), Some("last"))

        TestEtmpRegimeService.matchOrg(bcd, ecd) must be(true)
      }

      "all elements match regardless of case" in {
        val bcd = makeBusinessCustomerDetails("businessName", "SAPNUMBER", "safeId", isAGroup = true, Some("agentRef"))
        val ecd = makeEtmpDetails("BUSINESSNAME", "sapNumber", "SAFEID", isAGroup = true, "regimeRef", Some("AGENTREF"), Some("first"), Some("LAST"))

        TestEtmpRegimeService.matchOrg(bcd, ecd) must be(true)
      }

      "AgentRef are both None" in {
        val bcd = makeBusinessCustomerDetails("businessName", "sapNumber", "safeId", isAGroup = true, None)
        val ecd = makeEtmpDetails("businessName", "sapNumber", "safeId", isAGroup = true, "regimeRef", None, Some("firstLast"), Some("lastFirst"))

        TestEtmpRegimeService.matchOrg(bcd, ecd) must be(true)
      }

      "first and last name are both None" in {
        val bcd = makeBusinessCustomerDetails("businessName", "sapNumber", "safeId", isAGroup = true, None)
        val ecd = makeEtmpDetails("businessName", "sapNumber", "safeId", isAGroup = true, "regimeRef", None, None, None)

        TestEtmpRegimeService.matchOrg(bcd, ecd) must be(true)
      }
    }

    "return false" when {
      "sapNumber does not match" in {
        val bcd = makeBusinessCustomerDetails("businessName", "sapNumber", "safeId", isAGroup = true, Some("agentRef"))
        val ecd = makeEtmpDetails("businessName", "sapNumberAltered", "safeId", isAGroup = true, "regimeRef", Some("agentRef"), Some("first"), Some("last"))

        TestEtmpRegimeService.matchOrg(bcd, ecd) must be(false)
      }

      "safeId and regimeRef do not match" in {
        val bcd = makeBusinessCustomerDetails("businessName", "sapNumber", "safeIdAlt", isAGroup = true, Some("agentRef"))
        val ecd = makeEtmpDetails("businessName", "sapNumber", "safeId", isAGroup = true, "regimeRefAlt", Some("agentRef"), Some("first"), Some("last"))

        TestEtmpRegimeService.matchOrg(bcd, ecd) must be(false)
      }

      "business name does not match" in {
        val bcd = makeBusinessCustomerDetails("businessName", "sapNumber", "safeId", isAGroup = true, Some("agentRef"))
        val ecd = makeEtmpDetails("businessNameDiff", "sapNumber", "safeId", isAGroup = true, "regimeRef", Some("agentRef"), Some("firstLast"), Some("lastFirst"))

        TestEtmpRegimeService.matchOrg(bcd, ecd) must be(false)
      }

      "AgentRef does not match" in {
        val bcd = makeBusinessCustomerDetails("businessName", "sapNumber", "safeId", isAGroup = true, Some("differentAgentRef"))
        val ecd = makeEtmpDetails("businessName", "sapNumber", "safeId", isAGroup = true, "regimeRef", Some("agentRef"), Some("firstLast"), Some("lastFirst"))

        TestEtmpRegimeService.matchOrg(bcd, ecd) must be(false)
      }

      "first and last does not match None" in {
        val bcd = makeBusinessCustomerDetails("businessName", "sapNumber", "safeId", isAGroup = true, Some("differentAgentRef"))
        val ecd = makeEtmpDetails("businessName", "sapNumber", "safeId", isAGroup = true, "regimeRef", Some("agentRef"), None, None)

        TestEtmpRegimeService.matchOrg(bcd, ecd) must be(false)
      }
    }
  }

  "upsertEacdEnrolment" should {
    "upsert an eacd enrolment" when {

      "provided details to enrol for a CT business" in {
        when(mockTaxEnrolments.addKnownFacts(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse.apply(OK, "")))

        val result = TestEtmpRegimeService.upsertAtedKnownFacts(Some("UTR"), Some("postcode"), "atedRefNumber", "LTD")

        await(result).status must be(OK)
      }

      "provided details to enrol for a SOP business" in {
        when(mockTaxEnrolments.addKnownFacts(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse.apply(OK, "")))

        val result = TestEtmpRegimeService.upsertAtedKnownFacts(Some("UTR"), Some("postcode"), "atedRefNumber", "SOP")

        await(result).status must be(OK)
      }

      "provided only a tax ref" in {
        when(mockTaxEnrolments.addKnownFacts(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse.apply(OK, "")))

        val result = TestEtmpRegimeService.upsertAtedKnownFacts(Some("UTR"), None, "atedRefNumber", "LTD")

        await(result).status must be(OK)
      }
    }

    "failed to upsert eacd enrolment" when {

      "provided details to enrolment for LTD" in {
        when(mockTaxEnrolments.addKnownFacts(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.failed(new RuntimeException("failed to upsert enrolment")))

        val result = TestEtmpRegimeService.upsertAtedKnownFacts(Some("UTR"), Some("postcode"), "atedRefNumber", "LTD")
        intercept[RuntimeException](await(result))
      }

      "provided details to enrolment for SOP" in {
        when(mockTaxEnrolments.addKnownFacts(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.failed(new RuntimeException("failed to upsert enrolment")))

        val result = TestEtmpRegimeService.upsertAtedKnownFacts(Some("UTR"), Some("postcode"), "atedRefNumber", "SOP")
        intercept[RuntimeException](await(result))
      }

    }
  }
}