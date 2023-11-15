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

package services

import connectors.{EtmpConnector, GovernmentGatewayAdminConnector, TaxEnrolmentsConnector}

import javax.inject.Inject
import models.{KnownFact, KnownFactsForService, Verifier, Verifiers}
import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.{JsObject, JsValue}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import utils.BusinessTypeConstants._
import utils.GovernmentGatewayConstants._

import scala.concurrent.{ExecutionContext, Future}

class DefaultSubscribeService @Inject()(val etmpConnector: EtmpConnector,
                                        val ggAdminConnector: GovernmentGatewayAdminConnector,
                                        val taxEnrolmentsConnector: TaxEnrolmentsConnector,
                                        val servicesConfig: ServicesConfig) extends SubscribeService {
  val isEmacFeatureToggle: Boolean = servicesConfig.getBoolean("emacsFeatureToggle")
}

trait SubscribeService extends Logging {

  def etmpConnector: EtmpConnector
  def ggAdminConnector: GovernmentGatewayAdminConnector
  def taxEnrolmentsConnector: TaxEnrolmentsConnector

  val isEmacFeatureToggle: Boolean

  def subscribe(data: JsValue)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    for {
      submitResponse <- etmpConnector.subscribeAted(stripJsonForEtmp(data))
      _ <- addKnownFacts(submitResponse, data)
    } yield {
      submitResponse
    }
  }

  private def addKnownFacts(response: HttpResponse, data: JsValue)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    val isNonUKClientRegisteredByAgent = (data \ "isNonUKClientRegisteredByAgent").asOpt[Boolean].getOrElse(false)
    (isNonUKClientRegisteredByAgent, response.status) match {
      case (false, OK) =>
        if (isEmacFeatureToggle) {

          val postcode = ((data \ "postcode").asOpt[String], (data \ "knownFactPostcode").asOpt[String]) match {
            case (Some(x), _) if x.nonEmpty => Some(x)
            case (None, Some(x)) if x.nonEmpty => Some(x)
            case _ => None
          }

          taxEnrolmentsConnector.addKnownFacts(
            createEnrolmentVerifiers(
              utrType = getUtrType((data \ "businessType").as[String]),
              utr = (data \ "utr").asOpt[String],
              postcode = postcode,
            ),
            getAtedReference(response))
        } else {
          ggAdminConnector.addKnownFacts(createKnownFacts(response, data))
        }
      case _ =>
        Future.successful(response)
    }
  }

  private def stripJsonForEtmp(data: JsValue) = {
    data.as[JsObject] - "utr" - "isNonUKClientRegisteredByAgent" - "knownFactPostcode" - "businessType"
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

    if (utr.isEmpty && postCode.isEmpty) {
      throw new RuntimeException(s"[SubscribeService][createKnownFacts] - postalCode or utr must be supplied:: $data)")
    }

    val postCodeKnownFact = postCode.map(KnownFact(VerifierPostalCode, _))
    val utrKnownFact = utr.map(KnownFact(VerifierCtUtr, _))

    val utrAndPostcodeList = List(postCodeKnownFact, utrKnownFact).flatten

    KnownFactsForService(List(KnownFact(AtedReferenceNoType, getAtedReference(response))) ++ utrAndPostcodeList)
  }

  def getUtrType(businessType: String): String = {

    if(saBusinessTypes.contains(businessType)){
      VerifierSaUtr
    }else{
      VerifierCtUtr
    }
  }

  def createEnrolmentVerifiers(utrType: String, utr: Option[String], postcode: Option[String]): Verifiers = {
    (utr, postcode) match {
      case (Some(uniqueTaxRef), Some(ukClientPostCode)) =>
        Verifiers(List(Verifier(VerifierPostalCode, ukClientPostCode), Verifier(utrType, uniqueTaxRef)))
      case (None, Some(nonUkClientPostCode)) =>
        Verifiers(List(
          Verifier(VerifierNonUKPostalCode, nonUkClientPostCode))
        ) //N.B. Non-UK Clients might use the property UK Postcode or their own Non-UK Postal Code
      case (Some(uniqueTaxRef), None) =>
        Verifiers(List(Verifier(utrType, uniqueTaxRef)))
      case (_, _) =>
        throw new RuntimeException("[SubscribeService][createEnrolmentVerifiers] - postcode or utr must be supplied")
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
