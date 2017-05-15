scalaVersion in ThisBuild := "2.11.9"

bintrayOrganization in ThisBuild := Some("ovotech")
organization in ThisBuild := "com.ovoenergy"
licenses in ThisBuild += ("MIT", url("http://opensource.org/licenses/MIT"))

// Make ScalaTest write test reports that CirceCI understands
val testReportsDir = sys.env.getOrElse("CI_REPORTS", "target/reports")
testOptions in ThisBuild += Tests.Argument(TestFrameworks.ScalaTest, "-o", "-u", testReportsDir)

resolvers += Resolver.bintrayRepo("ovotech", "maven")

libraryDependencies ++= Seq(
  "com.sksamuel.avro4s" %% "avro4s-core" % "1.6.4-ovo-1",
  "org.apache.kafka" % "kafka-clients" % "0.10.0.1",
  "io.circe" %% "circe-parser" % "0.7.0",
  "org.slf4j" % "slf4j-api" % "1.7.21",
  "org.scalatest" %% "scalatest" % "3.0.1" % Test,
  "com.ovoenergy" %% "comms-kafka-messages" % "1.20" % Test,
  "org.slf4j" % "slf4j-simple" % "1.7.21" % Test
)
