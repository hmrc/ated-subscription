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

package metrics

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer.Context
import metrics.MetricsEnum.MetricsEnum
import javax.inject.Inject

class DefaultServiceMetrics @Inject()(val registry: MetricRegistry) extends ServiceMetrics
trait ServiceMetrics {
  val registry: MetricRegistry

  private val timers = Map(
    MetricsEnum.GgAdminAddKnownFacts -> registry.timer("gga-add-known-facts-client-response-timer"),
    MetricsEnum.EtmpSubscribeAted -> registry.timer("etmp-subscribe-client-response-timer"),
    MetricsEnum.EmacAddKnownFacts -> registry.timer("emac-upsert-an-enrolment-response-timer")

  )

  private val successCounters = Map(
    MetricsEnum.GgAdminAddKnownFacts -> registry.counter("gga-add-known-facts-client-success-counter"),
    MetricsEnum.EtmpSubscribeAted -> registry.counter("etmp-subscribe-client-returns-success-counter"),
    MetricsEnum.EmacAddKnownFacts -> registry.counter("emac-upsert-an-enrolment-success-counter")

  )

  private val failedCounters = Map(
    MetricsEnum.GgAdminAddKnownFacts -> registry.counter("gga-add-known-facts-client-failed-counter"),
    MetricsEnum.EtmpSubscribeAted -> registry.counter("etmp-subscribe-client-returns-failed-counter"),
    MetricsEnum.EmacAddKnownFacts -> registry.counter("emac-upsert-an-enrolment-failed-counter")
  )

  def startTimer(api: MetricsEnum): Context = timers(api).time()
  def incrementSuccessCounter(api: MetricsEnum): Unit = successCounters(api).inc()
  def incrementFailedCounter(api: MetricsEnum): Unit = failedCounters(api).inc()
}
