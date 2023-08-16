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

package controllers

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Named}
import scala.concurrent.{ExecutionContext, Future}

class StatusInfoController @Inject()(val auditConnector: AuditConnector,
                                     val etmpRegimeService: EtmpRegimeService,
                                     val enrolmentService: EnrolmentService,
                                     cc: ControllerComponents,
                                     @Named("appName") val appName: String)(implicit ec: ExecutionContext) extends BackendController(cc) with Logging {

  def enrolledUsers(safeID: String): Action[AnyContent] = Action.async { implicit request =>
    etmpRegimeService.getEtmpBusinessDetails(safeID).flatMap {
      case Some(details) =>
        enrolmentService.atedUsers(details.regimeRefNumber).map {
          case Right(users) => Ok(Json.toJson(users))
          case Left(err) =>
            Status(err)(s"""Error when checking enrolment store for ${details.regimeRefNumber}""")
        }
      case None =>
        logger.warn(s"""[ATED][enrolledUsers for safeID] - No business details found for safeID $safeID""")
        Future.successful(NotFound(s"""ATED enrolled Business Details not found for $safeID"""))
    }
  }

}