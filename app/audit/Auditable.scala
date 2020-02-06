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

package audit

import uk.gov.hmrc.play.audit.AuditExtensions
import uk.gov.hmrc.play.audit.model.{Audit, DataEvent}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

trait Auditable {

  val auditConnector: AuditConnector

  def appName: String = "ated-subscription"

  def audit: Audit = new Audit(appName, auditConnector)

  def sendDataEvent(transactionName: String,
                    path: String = "N/A",
                    tags: Map[String, String] = Map.empty[String, String],
                    detail: Map[String, String]
                   )(implicit hc: HeaderCarrier): Unit =
    audit.sendDataEvent(
      DataEvent(
        appName,
        auditType = transactionName,
        tags = AuditExtensions.auditHeaderCarrier(hc).toAuditTags(transactionName, path) ++ tags,
        detail = AuditExtensions.auditHeaderCarrier(hc).toAuditDetails(detail.toSeq: _*)
      )
    )

  def doFailedAudit(auditType: String, request: String, response: String)(implicit hc:HeaderCarrier): Unit = {
    val auditDetails = Map("request" -> request,
                           "response" -> response)

    sendDataEvent(auditType, detail = auditDetails)
  }
}
