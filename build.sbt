scalaVersion in ThisBuild := "2.11.8"

bintrayOrganization in ThisBuild := Some("ovotech")
organization in ThisBuild := "com.ovoenergy"
licenses in ThisBuild += ("MIT", url("http://opensource.org/licenses/MIT"))

// Make ScalaTest write test reports that CirceCI understands
val testReportsDir = sys.env.getOrElse("CI_REPORTS", "target/reports")
testOptions in ThisBuild += Tests.Argument(TestFrameworks.ScalaTest, "-o", "-u", testReportsDir)

val circeVersion = "0.7.0"
libraryDependencies ++= Seq(
  "io.circe"            %% "circe-core"               % circeVersion,
  "io.circe"            %% "circe-parser"             % circeVersion,
  "io.circe"            %% "circe-optics"             % circeVersion,
  "com.sksamuel.avro4s" %% "avro4s-core" % "1.6.2",
  "org.apache.kafka" % "kafka-clients" % "0.10.0.1",
  "org.slf4j" % "slf4j-api" % "1.7.21",
  "org.scalatest" %% "scalatest" % "3.0.1" % Test,
  "io.circe"            %% "circe-generic"            % circeVersion % Test
)
