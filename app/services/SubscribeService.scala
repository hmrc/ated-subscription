/*
 * Copyright 2018 HM Revenue & Customs
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

package services

import connectors.connectors.TaxEnrolmentsConnector
import connectors.{ETMPConnector, GovernmentGatewayAdminConnector}
import models.{KnownFact, KnownFactsForService, Verifier, Verifiers}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsObject, JsValue}
import utils.GovernmentGatewayConstants

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.config.RunMode

object SubscribeService extends SubscribeService {
  val etmpConnector: ETMPConnector = ETMPConnector
  val ggAdminConnector: GovernmentGatewayAdminConnector = GovernmentGatewayAdminConnector
  val taxEnrolmentsConnector: TaxEnrolmentsConnector = TaxEnrolmentsConnector
  val isEmacFeatureToggle: Boolean = runModeConfiguration.getBoolean("emacsFeatureToggle").getOrElse(true)
}

trait SubscribeService extends RunMode {

  def etmpConnector: ETMPConnector

  def ggAdminConnector: GovernmentGatewayAdminConnector

  def taxEnrolmentsConnector: TaxEnrolmentsConnector

  val isEmacFeatureToggle: Boolean

  def subscribe(data: JsValue)(implicit hc: HeaderCarrier) = {
    for {
      submitResponse <- etmpConnector.subscribeAted(stripJsonForEtmp(data))
      _ <- addKnownFacts(submitResponse, data)
    } yield {
      submitResponse
    }
  }

  private def addKnownFacts(response: HttpResponse, data: JsValue)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val isNonUKClientRegisteredByAgent = (data \ "isNonUKClientRegisteredByAgent").asOpt[Boolean].getOrElse(false)
    (isNonUKClientRegisteredByAgent, response.status) match {
      case (false, OK) =>
        if (isEmacFeatureToggle) {
          taxEnrolmentsConnector.addKnownFacts(createEnrolmentVerifiers(response, data), getAtedReference(response))
        } else
          ggAdminConnector.addKnownFacts(createKnownFacts(response, data))
      case _ =>
        Future.successful(response)
    }
  }

  private def stripJsonForEtmp(data: JsValue) = {
    data.as[JsObject] - "utr" - "isNonUKClientRegisteredByAgent" - "knownFactPostcode"
  }

  private def getUtrAndPostCode(data: JsValue): (Option[String], Option[String]) = {
    def getUtr: Option[String] = {
      (data \ "utr").asOpt[String] match {
        case Some(x) if !x.trim().isEmpty => Some(x)
        case _ => None
      }
    }

    def getPostcode: Option[String] = {
      (data \ "knownFactPostcode").asOpt[String] match {
        case Some(x) if !x.trim().isEmpty => Some(x)
        case _ => None
      }
    }

    (getUtr, getPostcode)
  }

  private def createKnownFacts(response: HttpResponse, data: JsValue) = {
    val (utr, postCode) = getUtrAndPostCode(data)

    if (utr.isEmpty && postCode.isEmpty)
      throw new RuntimeException(s"[SubscribeService][createKnownFacts] - postalCode or utr must be supplied:: $data)")

    val postCodeKnownFact = postCode.map(KnownFact(GovernmentGatewayConstants.VerifierPostalCode, _))
    val utrKnownFact = utr.map(KnownFact(GovernmentGatewayConstants.VerifierCtUtr, _))

    val utrAndPostcodeList = List(postCodeKnownFact, utrKnownFact).flatten

    KnownFactsForService(List(KnownFact(GovernmentGatewayConstants.AtedReferenceNoType, getAtedReference(response))) ++ utrAndPostcodeList)
  }

  private def createEnrolmentVerifiers(response: HttpResponse, data: JsValue): Verifiers = {
    getUtrAndPostCode(data) match {
      case (Some(uniqueTaxRef), Some(ukClientPostCode)) =>
        Verifiers(List(Verifier(GovernmentGatewayConstants.VerifierPostalCode, ukClientPostCode), Verifier(GovernmentGatewayConstants.VerifierCtUtr, uniqueTaxRef)))
      case (None, Some(nonUkClientPostCode)) =>
        Verifiers(List(Verifier(GovernmentGatewayConstants.VerifierNonUKPostalCode, nonUkClientPostCode))) //N.B. Non-UK Clients might use the property UK Postcode or their own Non-UK Postal Code
      case (Some(uniqueTaxRef), None) =>
        Verifiers(List(Verifier(GovernmentGatewayConstants.VerifierCtUtr, uniqueTaxRef)))
      case (_, _) =>
        throw new RuntimeException(s"[NewRegisterUserService][subscribeAted][createEMACEnrolRequest] - postalCode or utr must be supplied")
    }
  }

  private def getAtedReference(response: HttpResponse): String = {
    val json = response.json
    (json \ "atedRefNumber").asOpt[String] match {
      case Some(atedRef) => atedRef
      case _ => throw new RuntimeException("[SubscribeService][createKnownFacts] - atedRefNumber not returned from etmp subscribe")
    }
  }
}
