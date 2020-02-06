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

import javax.inject.Inject
import play.api.Logger
import play.api.http.Status._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, AuthorisedFunctions, User}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import connectors.{EtmpConnector, EnrolmentStoreConnector}
import models.{BusinessCustomerDetails, EnrolmentVerifiers, EtmpRegistrationDetails}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class EtmpRegimeService @Inject()(etmpConnector: EtmpConnector,
                                  val enrolmentStoreConnector: EnrolmentStoreConnector,
                                  val authConnector: AuthConnector) extends AuthorisedFunctions {

  private val ATED_SERVICE_NAME = "HMRC-ATED-ORG"

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

  private def createVerifiers(safeId: String, utr: Option[String], businessType: String, postcode: String) = {
    val utrTuple = businessType match {
      case "SOP" => "SAUTR" -> utr.getOrElse("")
      case _ => "CTUTR" -> utr.getOrElse("")
    }
    val verifierTuples = Seq(
      "Postcode" -> postcode,
      "SAFEID" -> safeId
    ) :+ utrTuple

    EnrolmentVerifiers(verifierTuples: _*)
  }

  def upsertEacdEnrolment(safeId: String,
                          utr: Option[String],
                          businessType: String,
                          postcode: String,
                          atedRefNumber: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val enrolmentKey = s"$ATED_SERVICE_NAME~ATEDRefNumber~$atedRefNumber"
    val enrolmentVerifiers = createVerifiers(safeId, utr, businessType, postcode)
    enrolmentStoreConnector.upsertEnrolment(enrolmentKey, enrolmentVerifiers)
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
                businessCustomerDetails.businessType.getOrElse(""),
                businessCustomerDetails.businessAddress.postcode.getOrElse("").replaceAll("\\s+", ""),
                etmpRegDetails.regimeRefNumber
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
    if(bcdValue.isEmpty && erdValue.isEmpty) {
      true
    } else {
      bcdValue.map(_.toUpperCase).contains(erdValue.map(_.toUpperCase).getOrElse(""))
    }
  }

  def matchOrg(bcd: BusinessCustomerDetails, erd: EtmpRegistrationDetails): Boolean = {
    Map(
      "businessName" -> erd.organisationName.map(_.toUpperCase).contains(bcd.businessName.toUpperCase),
      "sapNumber" -> (bcd.sapNumber.toUpperCase == erd.sapNumber.toUpperCase),
      "safeId" -> (bcd.safeId.toUpperCase == erd.safeId.toUpperCase),
      "agentRef" -> compareOptionalStrings(bcd.agentReferenceNumber, erd.agentReferenceNumber)
    ).partition{case (_, v) => v} match {
      case (_, failures) if failures.isEmpty => true
      case (_, failures) =>
        Logger.warn(s"[matchOrg] Could not match following details for organisation: $failures")
        false
    }
  }

}