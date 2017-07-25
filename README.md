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


### Helpers

This is a collection of classes for dealing with the kafka topics.  It enumerates all of the topics and events on offer and allows users to make type-safe producers and consumers for either aiven or legacy.
  
  ```
      import com.ovoenergy.comms.helpers.{Kafka, Topic}
      import com.ovoenergy.comms.serialisation.Codecs._

      val consumer: KafkaConsumer[String, Option[TriggeredV3]] = Kafka.aiven.triggered.v3.consumer
      val producer: KafkaProducer[String, TriggeredV2] = Kafka.legacy.triggered.v2.producer
  ```
    

### Test Helpers

Currently this consists of a utility to iterate over available topics
  
  ```
      import com.ovoenergy.comms.helpers.{Kafka, Topic}
      import ArbGenerator._
      import TopicListTraverser._
      import com.ovoenergy.comms.serialisation.Codecs._
      import shapeless._
      import org.scalacheck.Shapeless._
      
      val visitor = new TopicListVisitor {
          override def apply[E: SchemaFor : Arbitrary : ToRecord : FromRecord : ClassTag](topic: Topic[E]): Unit = {
            println(topic.name)
          }
        }
      TopicListTraverser(Kafka.legacy.allTopics, visitor)
  ```


## To release a new version

You will need to be a member of the `ovotech` organisation on Bintray.

```
$ sbt release
```

will run the tests, bump the version, build all the artifacts and publish them to Bintray.

The first time you run it, you will need to configure sbt with your Bintray credentials beforehand: `$ sbt bintrayChangeCredentials`
