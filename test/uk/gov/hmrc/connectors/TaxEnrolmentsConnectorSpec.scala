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

package uk.gov.hmrc.connectors

import connectors.{DefaultTaxEnrolmentsConnector, TaxEnrolmentsConnector}
import metrics.ServiceMetrics
import models.{AtedUsers, Verifier, Verifiers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TaxEnrolmentsConnectorSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach with ConnectorTest{

  val mockServiceMetrics: ServiceMetrics = app.injector.instanceOf[ServiceMetrics]
  val mockServiceUrl = "tax-enrolments"
  val mockWSHttp: HttpClientV2 = mock[HttpClientV2]
  val auditConnector: AuditConnector = app.injector.instanceOf[AuditConnector]
  val servicesConfig: ServicesConfig = app.injector.instanceOf[ServicesConfig]

  trait Setup extends ConnectorTest{
    val taxEnrolmentsconnector: TaxEnrolmentsConnector = new DefaultTaxEnrolmentsConnector(servicesConfig,
      auditConnector,
      mockServiceMetrics,
      mockHttpClient)
  }

  val createVerifiers: Verifiers = Verifiers(List(Verifier("AtedReferenceNoType", "AtedReferenceNoType"),
    Verifier("PostalCode", "PostalCode"),
    Verifier("CTUTR", "CTUTR")))

  "TaxEnrolmentsConnector" must {

    "for successful set of known facts, return success" in new Setup {
      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(mockHttpClient.put(any())(any)).thenReturn(requestBuilder)
      when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")))

      val result: Future[HttpResponse] = taxEnrolmentsconnector.addKnownFacts(createVerifiers, "ATED-123")
      await(result).status must be(NO_CONTENT)
    }

    "for unsuccessful set of known facts, return subscription response" in new Setup {
      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, "")))
      val result: Future[HttpResponse] = taxEnrolmentsconnector.addKnownFacts(createVerifiers, "ATED-123")
      await(result).status must be(BAD_REQUEST)
    }

    "for successful set of Ated users, return 200 success with Non-null Ated Users list" in new Setup {
      val atedUsersList: AtedUsers = AtedUsers(List("principalUserId1"), List("delegatedId1"))

      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

      val jsOnData: JsValue = Json.toJson(atedUsersList)

      when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(OK, jsOnData.toString())))
      val result: Future[Either[Int, AtedUsers]] = taxEnrolmentsconnector.getATEDGroups("ATED-123")
      await(result) must be(Right(atedUsersList))
    }

    "for successful set of Ated users, return 200 success with Nil Ated Users list" in new Setup {
      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")))
      val result: Future[Either[Int, AtedUsers]] = taxEnrolmentsconnector.getATEDGroups("ATED-123")
      await(result) must be(Right(AtedUsers(Nil, Nil)))
    }

    "for BadRequest response from enrolments backend, return a Bad request response" in new Setup {
      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, "")))
      val result: Future[Either[Int, AtedUsers]] = taxEnrolmentsconnector.getATEDGroups("ATED-123")
      await(result) must be(Left(BAD_REQUEST))
    }

    "for any other exception response from enrolments backend, return the same back to the caller" in new Setup {
      implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(NOT_FOUND, "")))
      val result: Future[Either[Int, AtedUsers]] = taxEnrolmentsconnector.getATEDGroups("ATED-123")
      await(result) must be(Left(NOT_FOUND))
    }
  }
}