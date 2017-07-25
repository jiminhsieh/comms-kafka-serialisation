package com.ovoenergy.comms.helpers

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.kafka.ConsumerSettings
import cakesolutions.kafka.KafkaConsumer.Conf
import cakesolutions.kafka.{KafkaConsumer, KafkaProducer}
import com.ovoenergy.comms.serialisation.Serialisation
import com.ovoenergy.comms.serialisation.Serialisation.{
  avroBinarySchemaRegistryDeserializer,
  avroDeserializer,
  avroSerializer
}
import com.ovoenergy.kafka.serialization.avro.SchemaRegistryClientSettings
import com.sksamuel.avro4s.{FromRecord, SchemaFor, ToRecord}
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.{KafkaConsumer => JKafkaConsumer}
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import scala.concurrent.duration._

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.reflect.ClassTag

case class Topic[E](configName: String)(implicit private val kafkaConfig: KafkaClusterConfig) {

  lazy val name: String = {
    kafkaConfig.topics
      .getOrElse(configName, throw new Exception(s"Failed to find topic $configName in config"))
  }

  lazy val groupId: String = kafkaConfig.groupId

  private def serializer(implicit schema: SchemaFor[E], toRecord: ToRecord[E]) = kafkaConfig.schemaRegistry match {
    case None => avroSerializer[E]
    case Some(registrySettings) => {
      val schemaRegistryClientSettings =
        SchemaRegistryClientSettings(registrySettings.url, registrySettings.username, registrySettings.password)
      Serialisation.avroBinarySchemaRegistrySerializer(schemaRegistryClientSettings, name)
    }
  }

  private def deserializer(implicit schemaFor: SchemaFor[E], fromRecord: FromRecord[E], classTag: ClassTag[E]) =
    kafkaConfig.schemaRegistry match {
      //If the config has a schema registry entry, then we assume it's using avro binary (e.g. aiven).  Otherwise we
      //assumed it's a standard avro string.
      case None => avroDeserializer[E]
      case Some(registrySettings) => {
        val schemaRegistryClientSettings =
          SchemaRegistryClientSettings(registrySettings.url, registrySettings.username, registrySettings.password)
        avroBinarySchemaRegistryDeserializer[E](schemaRegistryClientSettings, name)
      }
    }

  private def initialProducerSettings(implicit schema: SchemaFor[E], toRecord: ToRecord[E]) =
    KafkaProducer.Conf(new StringSerializer, serializer, kafkaConfig.hosts)

  private def producerSettings(implicit schemaFor: SchemaFor[E], toRecord: ToRecord[E], classTag: ClassTag[E]) =
    kafkaConfig.ssl
      .map(
        ssl =>
          initialProducerSettings
            .withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
            .withProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
                          Paths.get(ssl.keystore.location).toAbsolutePath.toString)
            .withProperty(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
            .withProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, ssl.keystore.password)
            .withProperty(SslConfigs.SSL_KEY_PASSWORD_CONFIG, ssl.keyPassword)
            .withProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, Paths.get(ssl.truststore.location).toString)
            .withProperty(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "JKS")
            .withProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ssl.truststore.password))
      .getOrElse(initialProducerSettings)

  def producer(implicit schemaFor: SchemaFor[E],
               toRecord: ToRecord[E],
               classTag: ClassTag[E]): KafkaProducer[String, E] = KafkaProducer(producerSettings)

  def publisher(implicit schemaFor: SchemaFor[E],
                toRecord: ToRecord[E],
                classTag: ClassTag[E]): (E) => Future[RecordMetadata] = {
    val localProducer = producer
    (event: E) =>
      localProducer.send(new ProducerRecord[String, E](name, event))
  }

  private def initialConsumerSettings(implicit actorSystem: ActorSystem,
                                      schemaFor: SchemaFor[E],
                                      fromRecord: FromRecord[E],
                                      classTag: ClassTag[E]) =
    ConsumerSettings(actorSystem, new StringDeserializer, deserializer)
      .withBootstrapServers(kafkaConfig.hosts)
      .withGroupId(kafkaConfig.hosts)

  def consumerSettings(implicit actorSystem: ActorSystem,
                       schemaFor: SchemaFor[E],
                       fromRecord: FromRecord[E],
                       classTag: ClassTag[E]): ConsumerSettings[String, Option[E]] =
    kafkaConfig.ssl
      .map(
        ssl =>
          initialConsumerSettings
            .withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
            .withProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
                          Paths.get(ssl.keystore.location).toAbsolutePath.toString)
            .withProperty(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
            .withProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, ssl.keystore.password)
            .withProperty(SslConfigs.SSL_KEY_PASSWORD_CONFIG, ssl.keyPassword)
            .withProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, Paths.get(ssl.truststore.location).toString)
            .withProperty(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "JKS")
            .withProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ssl.truststore.password))
      .getOrElse(initialConsumerSettings)

  def consumer(fromBeginning: Boolean = false)(implicit schemaFor: SchemaFor[E],
                                               fromRecord: FromRecord[E],
                                               classTag: ClassTag[E]): JKafkaConsumer[String, Option[E]] = {
    val initialConsumerSettings =
      Conf[String, Option[E]](new StringDeserializer, deserializer, kafkaConfig.hosts, groupId)
    val consumerSettings = kafkaConfig.ssl
      .map(
        ssl =>
          initialConsumerSettings
            .withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
            .withProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
                          Paths.get(ssl.keystore.location).toAbsolutePath.toString)
            .withProperty(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
            .withProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, ssl.keystore.password)
            .withProperty(SslConfigs.SSL_KEY_PASSWORD_CONFIG, ssl.keyPassword)
            .withProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, Paths.get(ssl.truststore.location).toString)
            .withProperty(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "JKS")
            .withProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ssl.truststore.password))
      .getOrElse(initialConsumerSettings)

    val resetOffset = if (fromBeginning) "earliest" else "latest"
    val consumer    = KafkaConsumer(consumerSettings.withProperty("auto.offset.reset", resetOffset))
    consumer.subscribe(Seq(name))
    consumer
  }

  def pollConsumer(pollTime: FiniteDuration = 30.second,
                   noOfEventsExpected: Int = 1,
                   condition: E => Boolean = (_: E) => true)(implicit schemaFor: SchemaFor[E],
                                                             fromRecord: FromRecord[E],
                                                             classTag: ClassTag[E]): Seq[E] = {
    val theConsumer = consumer(fromBeginning = true)
    @tailrec
    def poll(deadline: Deadline, events: Seq[E]): Seq[E] = {
      if (deadline.hasTimeLeft) {
        val polledEvents: Seq[E] = theConsumer
          .poll(250)
          .records(name)
          .flatMap(_.value())
          .filter(condition)
          .toSeq
        val eventsSoFar: Seq[E] = events ++ polledEvents
        eventsSoFar.length match {
          case n if n == noOfEventsExpected => eventsSoFar
          case exceeded if exceeded > noOfEventsExpected =>
            throw new Exception(s"Consumed more than $noOfEventsExpected events from $name")
          case _ => poll(deadline, eventsSoFar)
        }
      } else
        throw new Exception("Events didn't appear within the timelimit")
    }
    try {
      poll(pollTime.fromNow, Nil)
    } finally {
      theConsumer.close()
    }
  }
}
