package com.ovoenergy.comms.akka.streams

import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, ProducerSettings}
import com.ovoenergy.comms.serialisation.Serialisation
import com.ovoenergy.kafka.serialization.avro.SchemaRegistryClientSettings
import com.sksamuel.avro4s.{FromRecord, SchemaFor, ToRecord}
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}

import scala.reflect.ClassTag

object Factory {

  case class KafkaConfig(groupId: String, hosts: String, topic: String)

  def consumerSettings[T: SchemaFor: FromRecord: ClassTag](schemaRegistryClientSettings: SchemaRegistryClientSettings, kafkaConfig: KafkaConfig, actorSystem: ActorSystem): ConsumerSettings[String, Option[T]] = {
    val deserialiser = Serialisation.avroBinarySchemaRegistryDeserializer(schemaRegistryClientSettings, kafkaConfig.topic)

      ConsumerSettings(actorSystem, new StringDeserializer, deserialiser)
        .withBootstrapServers(kafkaConfig.hosts)
        .withGroupId(kafkaConfig.groupId)
  }

  def producerSettings[T: SchemaFor: ToRecord: ClassTag](schemaRegistryClientSettings: SchemaRegistryClientSettings, kafkaConfig: KafkaConfig, actorSystem: ActorSystem): ProducerSettings[String, T] = {
    val serializer = Serialisation.avroBinarySchemaRegistrySerializer(schemaRegistryClientSettings, kafkaConfig.topic)

      ProducerSettings(actorSystem, new StringSerializer, serializer)
        .withBootstrapServers(kafkaConfig.hosts)
  }

}
