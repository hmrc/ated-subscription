import sbt._

object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-28" % "7.21.0"
  )

  def apply(): Seq[sbt.ModuleID] = compile ++ Test()

  trait TestDependencies {
    lazy val scope: String = "it,test"
    val test: Seq[ModuleID]
  }

  object Test {
    def apply(): Seq[sbt.ModuleID] = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc"                  %% "bootstrap-test-play-28" % "7.21.0"            % scope,
        "org.scalatestplus.play"       %% "scalatestplus-play"     % "5.1.0"             % scope,
        "com.typesafe.play"            %% "play-test"              % PlayVersion.current % scope,
        "org.mockito"                  %  "mockito-core"           % "5.4.0"             % scope,
        "org.scalatestplus"            %% "mockito-4-11"           % "3.2.16.0"          % scope,
        "com.fasterxml.jackson.module" %% "jackson-module-scala"   % "2.15.2"            % scope,
        "com.github.tomakehurst"       %  "wiremock-jre8"          % "2.35.0"            % IntegrationTest withSources()
      )
    }.test
  }

}
