package com.ovoenergy.comms.akka.streams

import java.nio.file.Path

import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, ProducerSettings}
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


  def consumerSettings[T: SchemaFor: FromRecord: ClassTag](schemaRegistryClientSettings: SchemaRegistryClientSettings,
                                                           kafkaConfig: KafkaConfig)
                                                          (implicit actorSystem: ActorSystem): ConsumerSettings[String, Option[T]] = {
    val deserialiser = Serialisation.avroBinarySchemaRegistryDeserializer(schemaRegistryClientSettings, kafkaConfig.topic)

    val settings = ConsumerSettings(actorSystem, new StringDeserializer, deserialiser)
      .withBootstrapServers(kafkaConfig.hosts)
      .withGroupId(kafkaConfig.groupId)

    kafkaConfig.ssl.fold(settings){ ssl =>
      settings
        .withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
        .withProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, ssl.keystoreLocation.toAbsolutePath.toString)
        .withProperty(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, ssl.keystoreType.toString)
        .withProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, ssl.keystorePassword)
        .withProperty(SslConfigs.SSL_KEY_PASSWORD_CONFIG, ssl.keyPassword)
        .withProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, ssl.truststoreLocation.toString)
        .withProperty(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, ssl.truststoreType.toString)
        .withProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ssl.truststorePassword)
    }
  }

  def producerSettings[T: SchemaFor: ToRecord: ClassTag](schemaRegistryClientSettings: SchemaRegistryClientSettings,
                                                         kafkaConfig: KafkaConfig)
                                                        (implicit actorSystem: ActorSystem): ProducerSettings[String, T] = {
    val serializer = Serialisation.avroBinarySchemaRegistrySerializer(schemaRegistryClientSettings, kafkaConfig.topic)

    val settings = ProducerSettings(actorSystem, new StringSerializer, serializer)
      .withBootstrapServers(kafkaConfig.hosts)

    kafkaConfig.ssl.fold(settings){ ssl =>
      settings
        .withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
        .withProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, ssl.keystoreLocation.toAbsolutePath.toString)
        .withProperty(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, ssl.keystoreType.toString)
        .withProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, ssl.keystorePassword)
        .withProperty(SslConfigs.SSL_KEY_PASSWORD_CONFIG, ssl.keyPassword)
        .withProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, ssl.truststoreLocation.toString)
        .withProperty(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, ssl.truststoreType.toString)
        .withProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ssl.truststorePassword)
    }
  }

}
