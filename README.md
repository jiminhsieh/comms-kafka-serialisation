# Comms platform Kafka Avro serialisation

[ ![CircleCI](https://circleci.com/gh/ovocomms/comms-kafka-serialisation/tree/master.svg?style=svg) ](https://circleci.com/gh/ovocomms/comms-kafka-serialisation/tree/master)

[![Download](https://api.bintray.com/packages/ovocomms/maven/comms-kafka-serialisation/images/download.svg)](https://bintray.com/ovocomms/maven/comms-kafka-serialisation/_latestVersion)

Utility functions to generate Kakfa (de)serialisers for case classes with Avro schemas

## How to use

Add a resolver in sbt for the Maven repo on Bintray:

```
resolvers += Resolver.bintrayRepo("ovocomms", "maven")
```

Then add a dependency on the library:

```
libraryDependencies += "com.ovoenergy" %% "comms-kafka-serialisation" % "version"
```

See the Bintray badge above for the latest version.

Then in your code:

```
import com.ovoenergy.comms.serialisation._

val deserializer = Serialisation.avroDeserializer[MyLovelyKafkaEvent]
val result: Option[MyLovelyKafkaEvent] = deserializer.deserialize("my-topic", messageBytes)
```

## To release a new version

You will need to be a member of the `ovocomms` organisation on Bintray.

```
$ sbt release
```

will run the tests, bump the version, build all the artifacts and publish them to Bintray.

The first time you run it, you will need to configure sbt with your Bintray credentials beforehand: `$ sbt bintrayChangeCredentials`
