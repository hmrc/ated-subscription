import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-30" % "9.1.0"
  )
  def apply(): Seq[ModuleID] = compile ++ test

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"   %% "bootstrap-test-play-30" % "9.1.0",
  ).map(_ % Test)

  val itDependencies: Seq[ModuleID] = Seq()
}
