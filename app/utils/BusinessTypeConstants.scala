/*
 * Copyright 2022 HM Revenue & Customs
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

package utils

object BusinessTypeConstants {

  val limitedCompany = "Corporate Body"
  val businessPartnership = "Partnership"
  val limitedPartnership = "Partnership"
  val limitedLiabilityPartnership = "LLP"
  val unitTrust = "Corporate Body"
  val unlimitedCompany = "Corporate Body"
  val overseasCompany = "Non UK-based Company"
  val soleTrader = "Sole Trader"

  def saBusinessTypes: List[String] = {
    List(businessPartnership, limitedPartnership, limitedLiabilityPartnership, overseasCompany, soleTrader)
  }

  def allBusinessTypes: List[String] = {
    saBusinessTypes.++(List(limitedCompany, unitTrust, unlimitedCompany))
  }

}
