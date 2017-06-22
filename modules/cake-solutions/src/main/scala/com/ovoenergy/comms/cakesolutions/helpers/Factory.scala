package com.ovoenergy.comms.cakesolutions.helpers

import java.nio.file.Path

import cakesolutions.kafka.KafkaConsumer.{Conf => ConsumerConf}
import cakesolutions.kafka.KafkaProducer.{Conf => ProducerConf}
import cakesolutions.kafka.{KafkaConsumer, KafkaProducer}
import com.ovoenergy.comms.serialisation.Serialisation
import com.ovoenergy.kafka.serialization.avro.SchemaRegistryClientSettings
import com.sksamuel.avro4s.{FromRecord, SchemaFor, ToRecord}
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}

import scala.reflect.ClassTag

object Factory {

  sealed trait StoreType
  object StoreType {
    case object JKS extends StoreType
    case object PKCS12 extends StoreType
  }

  case class SSLConfig(
                        keystoreLocation: Path,
                        keystoreType: StoreType,
                        keystorePassword: String,
                        keyPassword: String,
                        truststoreLocation: Path,
                        truststoreType: StoreType,
                        truststorePassword: String
                      )

  case class KafkaConfig(
                          groupId: String,
                          hosts: String,
                          topic: String,
                          ssl: Option[SSLConfig]
                        )

  def producer[T: ToRecord: SchemaFor](schemaRegistryClientSettings: SchemaRegistryClientSettings,
                                       kafkaConfig: KafkaConfig) = {
    val serialiser = Serialisation.avroBinarySchemaRegistrySerializer[T](schemaRegistryClientSettings, kafkaConfig.topic)

    val conf = ProducerConf(new StringSerializer, serialiser, kafkaConfig.hosts)

    val confWithSsl = kafkaConfig.ssl.fold(conf){ ssl =>
      conf
        .withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
        .withProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, ssl.keystoreLocation.toAbsolutePath.toString)
        .withProperty(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, ssl.keystoreType.toString)
        .withProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, ssl.keystorePassword)
        .withProperty(SslConfigs.SSL_KEY_PASSWORD_CONFIG, ssl.keyPassword)
        .withProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, ssl.truststoreLocation.toString)
        .withProperty(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, ssl.truststoreType.toString)
        .withProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ssl.truststorePassword)
    }

    KafkaProducer(confWithSsl)
  }

  def consumer[T: FromRecord: SchemaFor: ClassTag](schemaRegistryClientSettings: SchemaRegistryClientSettings,
                                                   kafkaConfig: KafkaConfig) = {
    val deserialiser = Serialisation.avroBinarySchemaRegistryDeserializer[T](schemaRegistryClientSettings, kafkaConfig.topic)

    val conf = ConsumerConf(new StringDeserializer, deserialiser, bootstrapServers = kafkaConfig.hosts, kafkaConfig.groupId)

    val confWithSsl = kafkaConfig.ssl.fold(conf){ ssl =>
      conf
        .withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
        .withProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, ssl.keystoreLocation.toAbsolutePath.toString)
        .withProperty(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, ssl.keystoreType.toString)
        .withProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, ssl.keystorePassword)
        .withProperty(SslConfigs.SSL_KEY_PASSWORD_CONFIG, ssl.keyPassword)
        .withProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, ssl.truststoreLocation.toString)
        .withProperty(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, ssl.truststoreType.toString)
        .withProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ssl.truststorePassword)
    }

    KafkaConsumer(confWithSsl)
  }

}
