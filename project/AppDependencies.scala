import sbt._

object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-26" % "1.13.0"
  )

  def apply() = compile ++ Test()

  trait TestDependencies {
    lazy val scope: String = "it,test"
    val test: Seq[ModuleID]
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "org.scalatest" %% "scalatest" % "3.0.8" % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3" % scope,
        "org.pegdown" % "pegdown" % "1.6.0" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.mockito" % "mockito-core" % "3.2.4" % scope,
        "com.github.tomakehurst" % "wiremock-jre8" % "2.26.3" % IntegrationTest withSources()
      )
    }.test
  }

}
