package com.ovoenergy.comms.helpers

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.kafka.ConsumerSettings
import cakesolutions.kafka.KafkaProducer
import com.ovoenergy.comms.serialisation.Retry._
import com.ovoenergy.comms.serialisation.Serialisation._
import com.ovoenergy.kafka.serialization.avro.SchemaRegistryClientSettings
import com.sksamuel.avro4s.{FromRecord, SchemaFor, ToRecord}
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.{Deserializer, Serializer, StringDeserializer, StringSerializer}

import scala.concurrent.Future
import scala.reflect.ClassTag

case class Topic[E](configName: String)(implicit val kafkaConfig: KafkaClusterConfig) {

  lazy val name: String = {
    kafkaConfig.topics
      .getOrElse(configName, throw new Exception(s"Failed to find topic $configName in config"))
  }

  lazy val groupId: String = kafkaConfig.groupId

  def serializer(implicit schema: SchemaFor[E], toRecord: ToRecord[E]): Either[Failed, Serializer[E]] =
    kafkaConfig.schemaRegistry match {
      case None => Right(avroSerializer[E])
      case Some(registrySettings) => {
        val schemaRegistryClientSettings =
          SchemaRegistryClientSettings(registrySettings.url, registrySettings.username, registrySettings.password)
        avroBinarySchemaRegistrySerializer(schemaRegistryClientSettings, name, registrySettings.retry)
      }
    }

  def deserializer(implicit schemaFor: SchemaFor[E],
                   fromRecord: FromRecord[E],
                   classTag: ClassTag[E]): Either[Failed, Deserializer[Option[E]]] =
    kafkaConfig.schemaRegistry match {
      //If the config has a schema registry entry, then we assume it's using avro binary (e.g. aiven).  Otherwise we
      //assumed it's a standard avro string.
      case None => Right(avroDeserializer[E])
      case Some(registrySettings) => {
        val schemaRegistryClientSettings =
          SchemaRegistryClientSettings(registrySettings.url, registrySettings.username, registrySettings.password)
        avroBinarySchemaRegistryDeserializer[E](schemaRegistryClientSettings, name, registrySettings.retry)
      }
    }

  private def initialProducerSettings(implicit schema: SchemaFor[E],
                                      toRecord: ToRecord[E]): Either[Failed, KafkaProducer.Conf[String, E]] = {
    serializer.right.map { s =>
      KafkaProducer.Conf(new StringSerializer, s, kafkaConfig.hosts)
    }
  }

  private def producerSettings(implicit schemaFor: SchemaFor[E],
                               toRecord: ToRecord[E],
                               classTag: ClassTag[E]): Either[Failed, KafkaProducer.Conf[String, E]] = {
    val sslOpt = kafkaConfig.ssl
    if (sslOpt.isDefined) {
      val ssl = sslOpt.get
      handleEither(initialProducerSettings) { settings =>
        settings
          .withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
          .withProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
                        Paths.get(ssl.keystore.location).toAbsolutePath.toString)
          .withProperty(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
          .withProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, ssl.keystore.password)
          .withProperty(SslConfigs.SSL_KEY_PASSWORD_CONFIG, ssl.keyPassword)
          .withProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, Paths.get(ssl.truststore.location).toString)
          .withProperty(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "JKS")
          .withProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ssl.truststore.password)
      }
    } else {
      initialProducerSettings
    }
  }

  def producer(implicit schemaFor: SchemaFor[E],
               toRecord: ToRecord[E],
               classTag: ClassTag[E]): Either[Failed, KafkaProducer[String, E]] = {
    handleEither(producerSettings)((settings: KafkaProducer.Conf[String, E]) => KafkaProducer.apply(settings))
  }

  private def handleEither[A, B](eitherVal: Either[Failed, A])(f: A => B): Either[Failed, B] =
    eitherVal.right.map(r => f(r))

  def publisher(implicit schemaFor: SchemaFor[E],
                toRecord: ToRecord[E],
                classTag: ClassTag[E]): Either[Failed, (E) => Future[RecordMetadata]] = {
    val localProducer = producer

    handleEither(localProducer) { producer => (event: E) =>
      producer.send(new ProducerRecord[String, E](name, event))
    }

  }

  def retryPublisher(implicit schemaFor: SchemaFor[E],
                     toRecord: ToRecord[E],
                     classTag: ClassTag[E],
                     eventLogger: EventLogger[E],
                     hasCommName: HasCommName[E],
                     actorSystem: ActorSystem): Either[Failed, (E) => Future[RecordMetadata]] = {
    val localProducer = producer

    kafkaConfig.retry match {
      case None => throw new Exception("Unable to find config for retry")
      case Some(retry) => {
        handleEither(localProducer) { producer => (event: E) =>
          {
            implicit val scheduler = actorSystem.scheduler
            import scala.concurrent.ExecutionContext.Implicits.global
            retryAsync(
              config = retry,
              onFailure = e => {
                eventLogger.warn(event, s"Failed to send Kafka event to topic $name", e)
              }
            ) { () =>
              producer.send(new ProducerRecord[String, E](name, hasCommName.commName(event), event)).map { record =>
                eventLogger.info(event, s"Event posted to $name")
                record
              }
            }
          }
        }
      }
    }
  }

  private def initialConsumerSettings(implicit actorSystem: ActorSystem,
                                      schemaFor: SchemaFor[E],
                                      fromRecord: FromRecord[E],
                                      classTag: ClassTag[E]): Either[Failed, ConsumerSettings[String, Option[E]]] = {

    handleEither(deserializer) {
      ConsumerSettings
        .apply(actorSystem, new StringDeserializer, _)
        .withBootstrapServers(kafkaConfig.hosts)
        .withGroupId(groupId)
    }
  }

  def consumerSettings(implicit actorSystem: ActorSystem,
                       schemaFor: SchemaFor[E],
                       fromRecord: FromRecord[E],
                       classTag: ClassTag[E]): Either[Failed, ConsumerSettings[String, Option[E]]] = {

    val sslOpt = kafkaConfig.ssl

    if (sslOpt.isDefined) {
      val ssl = sslOpt.get
      handleEither(initialConsumerSettings) { settings =>
        settings
          .withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
          .withProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
                        Paths.get(ssl.keystore.location).toAbsolutePath.toString)
          .withProperty(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
          .withProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, ssl.keystore.password)
          .withProperty(SslConfigs.SSL_KEY_PASSWORD_CONFIG, ssl.keyPassword)
          .withProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, Paths.get(ssl.truststore.location).toString)
          .withProperty(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "JKS")
          .withProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ssl.truststore.password)
      }
    } else {
      initialConsumerSettings
    }
  }
}
