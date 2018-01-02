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
  scalaVersion := "2.12.2",
  crossScalaVersions += "2.11.12",
  organization := "com.ovoenergy",
  licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
)

lazy val root = Project("root", file("."))
  .settings(commonSettings)
  .settings(
    releasePublishArtifactsAction := {},
    publishArtifact := false
  )
  .aggregate(serialisation, helpers, testHelpers)

lazy val serialisation = Project("comms-kafka-serialisation", file("modules/serialisation"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.sksamuel.avro4s" %% "avro4s-core"                % "1.6.4-ovo-1",
      "org.apache.kafka"    % "kafka-clients"               % "0.10.2.1",
      "com.ovoenergy"       %% "kafka-serialization-core"   % "0.1.18",
      "com.ovoenergy"       %% "kafka-serialization-avro4s" % "0.1.18",
      "org.slf4j"           % "slf4j-api"                   % "1.7.21",
      "com.typesafe.akka"   %% "akka-stream-kafka"          % "0.17",
      "org.scalatest"       %% "scalatest"                  % "3.0.3" % Test,
      "com.ovoenergy"       %% "comms-kafka-messages"       % "1.40" % Test,
      "org.slf4j"           % "slf4j-simple"                % "1.7.21" % Test
    )
  )

lazy val helpers = Project("comms-kafka-helpers", file("modules/helpers"))
  .settings(commonSettings)
  .dependsOn(serialisation)
  .settings(
    resolvers ++= Seq(
      Resolver.bintrayRepo("cakesolutions", "maven")
    ),
    libraryDependencies ++= Seq(
      "net.cakesolutions"     %% "scala-kafka-client"   % "0.10.2.2",
      "com.github.pureconfig" %% "pureconfig"           % "0.7.2",
      "com.ovoenergy"         %% "comms-kafka-messages" % "1.40",
      "com.chuusai"           %% "shapeless"            % "2.3.2",
      "com.typesafe.akka"     %% "akka-stream-kafka"    % "0.17",
      "org.scalatest"         %% "scalatest"            % "3.0.3" % Test
    )
  )

lazy val testHelpers = Project("comms-kafka-test-helpers", file("modules/test-helpers"))
  .settings(commonSettings)
  .dependsOn(helpers)
  .settings(
    resolvers ++= Seq(
      Resolver.bintrayRepo("cakesolutions", "maven")
    ),
    libraryDependencies ++= Seq(
      "com.chuusai"                %% "shapeless"                 % "2.3.2",
      "net.manub"                  %% "scalatest-embedded-kafka"  % "0.15.1",
      "io.confluent"               % "kafka-schema-registry"      % "3.2.1" exclude ("org.apache.kafka", "kafka_2.11"),
      "org.scalacheck"             %% "scalacheck"                % "1.13.4",
      "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % "1.1.4"
    )
  )

releaseCrossBuild := true
scalafmtOnCompile in ThisBuild := true
