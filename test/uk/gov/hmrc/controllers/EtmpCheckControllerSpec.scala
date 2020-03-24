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

package uk.gov.hmrc.controllers

import controllers.EtmpCheckController
import models.EtmpRegistrationDetails
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.EtmpRegimeService
import uk.gov.hmrc.utils.TestJson

import scala.concurrent.Future

class EtmpCheckControllerSpec extends PlaySpec with MockitoSugar with TestJson with GuiceOneServerPerSuite {

  val mockEtmpRegimeService: EtmpRegimeService = mock[EtmpRegimeService]
  val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]

  object TestEtmpCheckController extends EtmpCheckController(cc, mockEtmpRegimeService)

  "checkEtmpBusinessPartnerExists" should {
    "return OK" when {
      "valid business customer details are received" when {
        "there are ETMP registration details" in {
          val etmpRegistrationDetails = EtmpRegistrationDetails(
            None,
            "sapNumber",
            "safeId",
            None,
            "regRef",
            None
          )

          when(mockEtmpRegimeService.checkEtmpBusinessPartnerExists(ArgumentMatchers.any(),
            ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
            .thenReturn(Future.successful(Some(etmpRegistrationDetails)))

          val result = TestEtmpCheckController.checkEtmp().apply(FakeRequest().withJsonBody(etmpCheckOrganisation))

          status(result) must be(OK)
          contentAsString(result) mustBe Json.obj("regimeRefNumber" -> "regRef").toString()
        }
      }
    }
    "return NO CONTENT" when {
      "invalid business customer details are received" in {
        val result = TestEtmpCheckController.checkEtmp().apply(
          FakeRequest().withJsonBody(etmpCheckOrganisationInvalid))

        status(result) must be(NO_CONTENT)
      }

      "no json body is received" in {
        val result = TestEtmpCheckController.checkEtmp().apply(
          FakeRequest())

        status(result) must be(NO_CONTENT)
      }

      "valid business customer details are received" when {
        "there aren't any ETMP registration details" in {
          when(mockEtmpRegimeService.checkEtmpBusinessPartnerExists(ArgumentMatchers.any(),
            ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
            .thenReturn(Future.successful(None))

          val result = TestEtmpCheckController.checkEtmp().apply(
            FakeRequest().withJsonBody(etmpCheckOrganisation))

          status(result) must be(NO_CONTENT)
        }
      }
    }
  }
}
