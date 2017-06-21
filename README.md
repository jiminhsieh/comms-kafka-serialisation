# Comms platform Kafka Avro serialisation

[ ![CircleCI](https://circleci.com/gh/ovotech/comms-kafka-serialisation/tree/master.svg?style=svg) ](https://circleci.com/gh/ovotech/comms-kafka-serialisation/tree/master)

[![Download](https://api.bintray.com/packages/ovotech/maven/comms-kafka-serialisation/images/download.svg)](https://bintray.com/ovotech/maven/comms-kafka-serialisation/_latestVersion)

This contains various utility functions to generate Kakfa serialisers, deserialisers, producers and consumers for case classes with Avro schemas. It consists of three modules:

* comms-kafka-serialisation
* comms-kafka-cakesolutions-helpers
* comms-kafka-akka-helpers


## How to use

Add a resolver in sbt for the Maven repo on Bintray:

```
resolvers := Resolver.withDefaultResolvers(
    Seq(
      Resolver.bintrayRepo("ovotech", "maven"),
      "confluent-release" at "http://packages.confluent.io/maven/"
    )
  )
```

Then add a dependency on the library:

```
libraryDependencies += Seq("com.ovoenergy" %% "comms-kafka-serialisation" % "version",
"com.ovoenergy" %% "comms-kafka-cakesolutions-helpers" % "version",
"com.ovoenergy" %% "comms-kafka-akka-helpers" % "version"
)
```


See the Bintray badge above for the latest version.


### Serialisation 

Then in your code, for vanilla avro json (de)serialisers                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          with no dependency on a schema registry:

```
import com.ovoenergy.comms.serialisation._

val deserializer = Serialisation.avroDeserializer[MyLovelyKafkaEvent]
val result: Option[MyLovelyKafkaEvent] = deserializer.deserialize("my-topic", messageBytes)
```


Or for binary avro (de)serialisers with schema registry support:
 
 ```
 import com.ovoenergy.comms.serialisation._
 import com.ovoenergy.kafka.serialization.avro.SchemaRegistryClientSettings

 val schemaRegistrySettings = SchemaRegistryClientSettings("schemaRegistryEndpoint:666", Authentication.None, 10, 5)
 val deserializer = Serialisation.avroBinarySchemaRegistryDeserializer[MyLovelyKafkaEvent](schemaRegistrySettings, false, "my-topic")
 val result: Option[MyLovelyKafkaEvent] = deserializer.deserialize("my-topic", messageBytes)
 ```
 
Note that this functionality is a thin wrapper around the kafka-serialization library, more information on how the schema registry works on its [readme](https://github.com/ovotech/kafka-serialization). The only
difference in functionality is that this wrapper will register the schema with the schema registry immediately on startup when the de(serialiser) is created rather than lazily, and deserialised values will be 
optional, returning Optional.None when deserialisation fails.

### Akka helpers

The comms-kafka-akka-helpers module provides a couple of helper methods to generate consumer and producer settings, to be used with akka-streams-kafka. These use the binary Avro (de)serialisers shown in the example above. 

For example, to generate consumer settings:


```
    import akka.actor.ActorSystem
    import com.ovoenergy.comms.akka.streams.Factory.consumerSettings
    import com.ovoenergy.comms.serialisation.KafkaConfig

    val kafkaConfig = KafkaConfig("my-group-id", "kafka-host:9812", "my-topic")
    val actorSystem= ActorSystem.apply()
    val akkaConsumerSettings: ConsumerSettings[String, Option[MyLovelyKafkaEvent]] = consumerSettings[MyLovelyKafkaEvent](schemaRegistryClientSettings, kafkaConfig, actorSystem)

```


### Cakesolutions helpers

The comms-cakesolutions-helpers module provides a couple of helper methods to create kafka producers or consumers, using the binary Avro (de)serialisers just as the akka example above. So for example:
  
  ```
      import com.ovoenergy.comms.cakesolutions.helpers.Factory.consumer
      import org.apache.kafka.clients.consumer.KafkaConsumer

      val cakeSolutionsConsumer: KafkaConsumer[String, Option[MyLovelyKafkaEvent]] = consumer[MyLovelyKafkaEvent](schemaRegistryClientSettings, kafkaConfig)
  
  ```

## To release a new version

You will need to be a member of the `ovotech` organisation on Bintray.

```
$ sbt release
```

will run the tests, bump the version, build all the artifacts and publish them to Bintray.

The first time you run it, you will need to configure sbt with your Bintray credentials beforehand: `$ sbt bintrayChangeCredentials`
