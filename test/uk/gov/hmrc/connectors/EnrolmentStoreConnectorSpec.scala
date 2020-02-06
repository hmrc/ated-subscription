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

package uk.gov.hmrc.connectors

import connectors.EnrolmentStoreConnector
import models.EnrolmentVerifiers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import scala.concurrent.Future

class EnrolmentStoreConnectorSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockAppConfig: ServicesConfig = mock[ServicesConfig]
  val mockWSHttp: DefaultHttpClient = mock[DefaultHttpClient]
  val mockAudit: AuditConnector = mock[AuditConnector]

  class Setup {
    val connector = new EnrolmentStoreConnector(
      mockAudit,
      mockWSHttp,
      mockAppConfig
    )
  }

  override def beforeEach: Unit = {
    reset(mockAppConfig)
    reset(mockWSHttp)
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val verifiers: EnrolmentVerifiers = EnrolmentVerifiers("something" -> "something")

  "upsertEnrolment" must {
    "return a 204 - NO CONTENT" in new Setup {
      when(mockWSHttp.PUT[EnrolmentVerifiers, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result: Future[HttpResponse] = connector.upsertEnrolment("testKey", verifiers)
      await(result).status must be(NO_CONTENT)
    }

    "retry when not a 204 - NO CONTENT" in new Setup {
      when(mockWSHttp.PUT[EnrolmentVerifiers, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(
            Future.successful(HttpResponse(NOT_FOUND)),
            Future.successful(HttpResponse(OK)),
            Future.successful(HttpResponse(NO_CONTENT))
          )

      val result: Future[HttpResponse] = connector.upsertEnrolment("testKey", verifiers)
      await(result).status must be(NO_CONTENT)
    }

    "audit and return the response when there is no 204 - NO CONTENT within the retry limit (7)" in new Setup {
      when(mockWSHttp.PUT[EnrolmentVerifiers, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(
            Future.successful(HttpResponse(NOT_FOUND)),
            Future.successful(HttpResponse(OK)),
            Future.successful(HttpResponse(OK)),
            Future.successful(HttpResponse(OK)),
            Future.successful(HttpResponse(OK)),
            Future.successful(HttpResponse(OK)),
            Future.successful(HttpResponse(OK)),
            Future.successful(HttpResponse(BAD_REQUEST)),
            Future.successful(HttpResponse(OK))
          )

      val result: Future[HttpResponse] = connector.upsertEnrolment("testKey", verifiers)
      await(result).status must be(BAD_REQUEST)
    }

  }
}
