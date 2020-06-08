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

package services

import connectors.{EtmpConnector, TaxEnrolmentsConnector}
import javax.inject.Inject
import models.{BusinessCustomerDetails, EtmpRegistrationDetails, Verifier, Verifiers}
import play.api.Logger
import play.api.http.Status._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, AuthorisedFunctions, User}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import utils.GovernmentGatewayConstants._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class EtmpRegimeService @Inject()(etmpConnector: EtmpConnector,
                                  val taxEnrolmentsConnector: TaxEnrolmentsConnector,
                                  val authConnector: AuthConnector) extends AuthorisedFunctions {

  def getEtmpBusinessDetails(safeId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[EtmpRegistrationDetails]] = {
    etmpConnector.atedRegime(safeId).map { response =>
      Try(EtmpRegistrationDetails.etmpReader.reads(response.json)) match {
        case Success(value)   => value.asOpt
        case Failure(e)       =>
          Logger.info(s"[EtmpRegimeService][getEtmpBusinessDetails] Could not read ETMP response - $e")
          None
      }
    }
  }

  def checkAffinityAgainstEtmpDetails(etmpRegistrationDetails: EtmpRegistrationDetails,
                                      businessCustomerDetails: BusinessCustomerDetails)
                                     (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[EtmpRegistrationDetails]] = {
    authorised(User).retrieve(Retrievals.affinityGroup){ affGroup =>
      Future(compareAffinityAgainstRegDetails(affGroup, businessCustomerDetails, etmpRegistrationDetails))
    } recover {
      case _ =>
        None
    }
  }

  def compareAffinityAgainstRegDetails(affinityGroup: Option[AffinityGroup],
                                       bcd: BusinessCustomerDetails,
                                       erd: EtmpRegistrationDetails): Option[EtmpRegistrationDetails] = {
    affinityGroup match {
      case Some(AffinityGroup.Organisation) if matchOrg(bcd, erd)        => Some(erd)
      case _ => None
    }
  }

  private def createEnrolmentVerifiers(safeId: String, utr: Option[String], postcode: Option[String], businessType: String): Verifiers = {
    val safeIdVerifier = Verifier(VerifierSafeId, safeId)

    val utrType = businessType match {
      case "SOP" => VerifierSaUtr
      case _     => VerifierCtUtr
    }

    (utr, postcode) match {
      case (Some(uniqueTaxRef), Some(ukClientPostCode)) =>
        Verifiers(List(
          Verifier(VerifierPostalCode, ukClientPostCode),
          Verifier(utrType, uniqueTaxRef),
          safeIdVerifier)
        )
      case (None, Some(nonUkClientPostCode)) =>
        Verifiers(List(
          Verifier(VerifierNonUKPostalCode, nonUkClientPostCode),
          safeIdVerifier)
        )
      case (Some(uniqueTaxRef), None) =>
        Verifiers(List(Verifier(utrType, uniqueTaxRef), safeIdVerifier))
      case (_, _) =>
        throw new RuntimeException(s"[NewRegisterUserService][createEnrolmentVerifiers] - postalCode or utr must be supplied")
    }
  }

  def upsertEacdEnrolment(safeId: String,
                          utr: Option[String],
                          postcode: Option[String],
                          atedRefNumber: String,
                          businessType: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    def validateVerifier(value: Option[String]): Option[String] =
      value match {
        case Some(x) if !x.trim().isEmpty => Some(x)
        case _ => None
      }

    val enrolmentVerifiers = createEnrolmentVerifiers(safeId, validateVerifier(utr), validateVerifier(postcode), businessType)
    taxEnrolmentsConnector.addKnownFacts(enrolmentVerifiers, atedRefNumber)
  }

  def checkEtmpBusinessPartnerExists(safeId: String,
                                     businessCustomerDetails: BusinessCustomerDetails
                                    )(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[EtmpRegistrationDetails]] = {

      getEtmpBusinessDetails(safeId) flatMap {
        case Some(etmpRegDetails) =>
          checkAffinityAgainstEtmpDetails(etmpRegDetails, businessCustomerDetails) flatMap {
            case Some(_) =>
              upsertEacdEnrolment(
                safeId,
                businessCustomerDetails.utr,
                businessCustomerDetails.businessAddress.postcode.map(_.replaceAll("\\s+", "")),
                etmpRegDetails.regimeRefNumber,
                businessCustomerDetails.businessType.getOrElse("")
              ) map { response =>
                response.status match {
                  case NO_CONTENT => Some(etmpRegDetails)
                  case status =>
                    Logger.warn(s"[EtmpRegimeService][checkEtmpBusinessPartnerExists] " +
                      s"Failed to upsert to EACD - status: $status")
                    None
                }
              }
            case None =>
              Future.successful(None)
          }
        case None =>
          Future.successful(None)
      } recover {
        case e: Exception =>
          Logger.warn(s"[EtmpRegimeService][checkEtmpBusinessPartnerExists] Failed to check ETMP api :${e.getMessage}")
          None
      }
  }

  def compareOptionalStrings(bcdValue: Option[String], erdValue: Option[String]): Boolean = {
    if (bcdValue.isEmpty && erdValue.isEmpty) {
      true
    } else {
      bcdValue.map(_.toUpperCase).contains(erdValue.map(_.toUpperCase).getOrElse(""))
    }
  }

  def matchOrg(bcd: BusinessCustomerDetails, erd: EtmpRegistrationDetails): Boolean = {
    Map(
      "businessName" -> erd.organisationName.map(_.toUpperCase).contains(bcd.businessName.toUpperCase),
      "sapNumber"    -> (bcd.sapNumber.toUpperCase == erd.sapNumber.toUpperCase),
      "safeId"       -> (bcd.safeId.toUpperCase == erd.safeId.toUpperCase),
      "agentRef"     -> compareOptionalStrings(bcd.agentReferenceNumber, erd.agentReferenceNumber)
    ).partition{case (_, v) => v} match {
      case (_, failures) if failures.isEmpty => true
      case (_, failures) =>
        Logger.warn(s"[matchOrg] Could not match following details for organisation: $failures")
        false
    }
  }

}
