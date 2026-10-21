import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {

  val bootstrapPlayVersion = "10.8.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-30" % bootstrapPlayVersion
  )
  def apply(): Seq[ModuleID] = compile ++ test

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"   %% "bootstrap-test-play-30" % bootstrapPlayVersion,
  ).map(_ % Test)

  val itDependencies: Seq[ModuleID] = Seq()
}
