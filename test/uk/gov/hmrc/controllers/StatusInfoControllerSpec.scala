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

package uk.gov.hmrc.controllers

import controllers.StatusInfoController
import models.{AtedUsers, BusinessPartnerDetails}
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{EnrolmentService, EtmpRegimeService}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.{ExecutionContext, Future}

class StatusInfoControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockEnrolementService: EnrolmentService = mock[EnrolmentService]
  val mockRegimeService: EtmpRegimeService = mock[EtmpRegimeService]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  object StatusInfoControllerSpec extends StatusInfoController(mockAuditConnector, mockRegimeService, mockEnrolementService, cc, "ated")

  "For enrolledUsers, Status Info Controller " must {
    "return a OK response containing true if a reference number exists" in {
      val testSafeId = "safeId123"
      val testBusinessDetails = BusinessPartnerDetails(
        Some("testOrganisation"), "sapNumber123", "safe123",
        "regime-ref-number-123", Some("agent-ref-number-123"))
      val atedUsers = AtedUsers(List("ated-user", "principal-user-two"), List("delegated-user-one", "delegated-user-two"))
      when(mockRegimeService.getEtmpBusinessDetails(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(testBusinessDetails)))
      when(mockEnrolementService.atedUsers(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Right(atedUsers)))

      val result = StatusInfoControllerSpec.enrolledUsers(testSafeId).apply(FakeRequest())
      status(result) shouldBe OK

      contentAsJson(result) shouldBe Json.toJson(Some(atedUsers))
    }

    "return None if a reference number doesnt exist" in {
      val testSafeId = "safeId123"
      when(mockRegimeService.getEtmpBusinessDetails(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

      val result = StatusInfoControllerSpec.enrolledUsers(testSafeId).apply(FakeRequest())
      status(result) shouldBe NOT_FOUND
    }
  }

}
