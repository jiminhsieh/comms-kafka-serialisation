package com.ovoenergy.comms.cakesolutions.helpers

import cakesolutions.kafka.KafkaConsumer.{Conf => ConsumerConf}
import cakesolutions.kafka.KafkaProducer.{Conf => ProducerConf}
import cakesolutions.kafka.{KafkaConsumer, KafkaProducer}
import com.ovoenergy.comms.serialisation.Serialisation
import com.ovoenergy.kafka.serialization.avro.SchemaRegistryClientSettings
import com.sksamuel.avro4s.{FromRecord, SchemaFor, ToRecord}
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}

import scala.reflect.ClassTag

object Factory {

  case class KafkaConfig(groupId: String, hosts: String, topic: String)

  def producer[T: ToRecord: SchemaFor](schemaRegistryClientSettings: SchemaRegistryClientSettings, kafkaConfig: KafkaConfig) = {
    val deserialiser = Serialisation.avroBinarySchemaRegistrySerializer[T](schemaRegistryClientSettings, kafkaConfig.topic)
    KafkaProducer(ProducerConf(new StringSerializer, deserialiser, kafkaConfig.hosts))
  }

  def consumer[T: FromRecord: SchemaFor: ClassTag](schemaRegistryClientSettings: SchemaRegistryClientSettings, kafkaConfig: KafkaConfig) = {
    val deserialiser = Serialisation.avroBinarySchemaRegistryDeserializer[T](schemaRegistryClientSettings, kafkaConfig.topic)
    KafkaConsumer(ConsumerConf(new StringDeserializer, deserialiser, bootstrapServers = kafkaConfig.hosts, kafkaConfig.groupId))
  }

}
