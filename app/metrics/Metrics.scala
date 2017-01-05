/*
 * Copyright 2017 HM Revenue & Customs
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

import com.codahale.metrics.Timer
import com.codahale.metrics.Timer.Context
import metrics.MetricsEnum.MetricsEnum
import uk.gov.hmrc.play.graphite.MicroserviceMetrics

trait Metrics {

  def startTimer(api: MetricsEnum): Timer.Context
  def incrementSuccessCounter(api: MetricsEnum): Unit
  def incrementFailedCounter(api: MetricsEnum): Unit

}

object Metrics extends Metrics with MicroserviceMetrics {

  val registry = metrics.defaultRegistry

  val timers = Map(
    MetricsEnum.GgAdminAddKnownFacts -> registry.timer("gga-add-known-facts-client-response-timer"),
    MetricsEnum.EtmpSubscribeAted -> registry.timer("etmp-subscribe-client-response-timer")

  )

  val successCounters = Map(
    MetricsEnum.GgAdminAddKnownFacts -> registry.counter("gga-add-known-facts-client-success-counter"),
    MetricsEnum.EtmpSubscribeAted -> registry.counter("etmp-subscribe-client-returns-success-counter")

  )

  val failedCounters = Map(
    MetricsEnum.GgAdminAddKnownFacts -> registry.counter("gga-add-known-facts-client-failed-counter"),
    MetricsEnum.EtmpSubscribeAted -> registry.counter("etmp-subscribe-client-returns-failed-counter")
  )

  override def startTimer(api: MetricsEnum): Context = timers(api).time()

  override def incrementSuccessCounter(api: MetricsEnum): Unit = successCounters(api).inc()

  override def incrementFailedCounter(api: MetricsEnum): Unit = failedCounters(api).inc()

}
