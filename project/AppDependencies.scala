import sbt.*

object AppDependencies {

  import play.core.PlayVersion
  import play.sbt.PlayImport.*

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-30" % "8.5.0"
  )

  def apply(): Seq[ModuleID] = compile ++ test

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-test-play-30" % "8.5.0",
    "org.scalatestplus.play"       %% "scalatestplus-play"     % "7.0.1",
    "org.mockito"                  %  "mockito-core"           % "5.11.0" ,
    "org.scalatestplus"            %% "mockito-4-11"           % "3.2.18.0",
  ).map(_ % Test)

}
