import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "ated-subscription"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  private val microserviceBootstrapVersion = "6.9.0"
  private val domainVersion = "4.1.0"

  val compile = Seq(

    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    "com.kenshoo" %% "metrics-play" % "2.3.0_0.1.8",
    "com.codahale.metrics" % "metrics-graphite" % "3.0.2"
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "org.scalatest" %% "scalatest" % "2.2.6" % scope,
        "org.pegdown" % "pegdown" % "1.4.2" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % scope,
        "org.mockito" % "mockito-all" % "1.10.19" % scope
      )
    }.test
  }

  def apply() = compile ++ Test()
}

