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

import connectors.{EtmpConnector, TaxEnrolmentsConnector}
import javax.inject.Inject
import models.{BusinessCustomerDetails, BusinessPartnerDetails}
import play.api.Logging
import play.api.http.Status._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, AuthorisedFunctions, User}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class EtmpRegimeService @Inject()(etmpConnector: EtmpConnector,
                                  val subscribeService: SubscribeService,
                                  val taxEnrolmentsConnector: TaxEnrolmentsConnector,
                                  val authConnector: AuthConnector) extends AuthorisedFunctions with Logging {

  def getEtmpBusinessDetails(safeId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BusinessPartnerDetails]] = {
    etmpConnector.atedRegime(safeId).map { response =>
      Try(BusinessPartnerDetails.reads.reads(response.json)) match {
        case Success(value)   => value.asOpt
        case Failure(e)       =>
          logger.info(s"[EtmpRegimeService][getEtmpBusinessDetails] Could not read ETMP response - $e")
          None
      }
    }
  }

  def checkAffinityAgainstEtmpDetails(etmpRegistrationDetails: BusinessPartnerDetails,
                                      businessCustomerDetails: BusinessCustomerDetails)
                                     (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BusinessPartnerDetails]] = {
    authorised(User).retrieve(Retrievals.affinityGroup){ affGroup =>
      Future(compareAffinityAgainstRegDetails(affGroup, businessCustomerDetails, etmpRegistrationDetails))
    } recover {
      case _ =>
        None
    }
  }

  def compareAffinityAgainstRegDetails(affinityGroup: Option[AffinityGroup],
                                       bcd: BusinessCustomerDetails,
                                       erd: BusinessPartnerDetails): Option[BusinessPartnerDetails] = {
    affinityGroup match {
      case Some(AffinityGroup.Organisation) if matchOrg(bcd, erd)        => Some(erd)
      case _ => None
    }
  }

  def upsertAtedKnownFacts(utr: Option[String],
                           postcode: Option[String],
                           atedRefNumber: String,
                           businessType: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    def validateVerifier(value: Option[String]): Option[String] =
      value match {
        case Some(x) if !x.trim().isEmpty => Some(x)
        case _ => None
      }

    val utrType = subscribeService.getUtrType(businessType)
    val enrolmentVerifiers = subscribeService.createEnrolmentVerifiers(utrType, validateVerifier(utr), validateVerifier(postcode))
    taxEnrolmentsConnector.addKnownFacts(enrolmentVerifiers, atedRefNumber)
  }

  def checkEtmpBusinessPartnerExists(safeId: String,
                                     bcd: BusinessCustomerDetails
                                    )(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[BusinessPartnerDetails]] = {

      getEtmpBusinessDetails(safeId) flatMap {
        case Some(etmpRegDetails) =>
          checkAffinityAgainstEtmpDetails(etmpRegDetails, bcd) flatMap {
            case Some(_) =>
              upsertAtedKnownFacts(
                bcd.utr,
                bcd.businessAddress.postcode.map(_.replaceAll("\\s+", "")),
                etmpRegDetails.regimeRefNumber,
                bcd.businessType,
              ) map { response =>
                response.status match {
                  case NO_CONTENT => Some(etmpRegDetails)
                  case status =>
                    logger.warn(s"[EtmpRegimeService][checkEtmpBusinessPartnerExists] " +
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
          logger.warn(s"[EtmpRegimeService][checkEtmpBusinessPartnerExists] Failed to check ETMP api :${e.getMessage}")
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

  def matchOrg(bcd: BusinessCustomerDetails, erd: BusinessPartnerDetails): Boolean = {
    Map(
      "businessName" -> erd.organisationName.map(_.toUpperCase).contains(bcd.businessName.toUpperCase),
      "sapNumber"    -> (bcd.sapNumber.toUpperCase == erd.sapNumber.toUpperCase),
      "safeId"       -> (bcd.safeId.toUpperCase == erd.safeId.toUpperCase),
      "agentRef"     -> compareOptionalStrings(bcd.agentReferenceNumber, erd.agentReferenceNumber)
    ).partition{case (_, v) => v} match {
      case (_, failures) if failures.isEmpty => true
      case (_, failures) =>
        logger.warn(s"[matchOrg] Could not match following details for organisation: $failures")
        false
    }
  }

}
