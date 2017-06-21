// Make ScalaTest write test reports that CirceCI understands
val testReportsDir = sys.env.getOrElse("CI_REPORTS", "target/reports")
testOptions in ThisBuild += Tests.Argument(TestFrameworks.ScalaTest, "-o", "-u", testReportsDir)

val commonSettings = Seq(
  bintrayOrganization := Some("ovotech"),
  resolvers := Resolver.withDefaultResolvers(
    Seq(
      Resolver.bintrayRepo("ovotech", "maven"),
      "confluent-release" at "http://packages.confluent.io/maven/"
    )
  ),
  scalaVersion := "2.11.9",
  organization := "com.ovoenergy",
  licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
)

lazy val root = Project("root", file("."))
  .settings(commonSettings)
  .aggregate(serialisation, akkaStreams, cakeSolutions)

lazy val serialisation = Project("comms-kafka-serialisation", file("modules/serialisation"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.sksamuel.avro4s" %% "avro4s-core" % "1.6.4-ovo-1",
      "org.apache.kafka" % "kafka-clients" % "0.10.0.1",
      "io.circe" %% "circe-parser" % "0.7.0",
      "com.ovoenergy" %% "kafka-serialization-core" % "0.1.16",
      "com.ovoenergy" %% "kafka-serialization-avro4s" % "0.1.16",
      "org.slf4j" % "slf4j-api" % "1.7.21",
      "org.scalatest" %% "scalatest" % "3.0.1" % Test,
      "com.ovoenergy" %% "comms-kafka-messages" % "1.20" % Test,
      "org.slf4j" % "slf4j-simple" % "1.7.21" % Test
    )
  )

lazy val akkaStreams = Project("comms-kafka-akka-helpers", file("modules/akka-streams"))
  .settings(commonSettings)
  .dependsOn(serialisation)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream-kafka" % "0.16"
    )
  )

lazy val cakeSolutions = Project("comms-kafka-cakesolutions-helpers", file("modules/cake-solutions"))
  .settings(commonSettings)
  .dependsOn(serialisation)
  .settings(
    resolvers += Resolver.bintrayRepo("cakesolutions", "maven"),
    libraryDependencies ++= Seq(
      "net.cakesolutions" %% "scala-kafka-client" % "0.10.2.2"
    )
  )


