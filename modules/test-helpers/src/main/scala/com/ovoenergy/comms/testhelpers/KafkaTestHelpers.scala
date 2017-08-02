package com.ovoenergy.comms.testhelpers

import java.nio.file.Paths
import java.util.UUID

import cakesolutions.kafka.KafkaConsumer
import cakesolutions.kafka.KafkaConsumer.Conf
import com.ovoenergy.comms.helpers.Topic
import com.sksamuel.avro4s.{FromRecord, SchemaFor}
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.{KafkaConsumer => JKafkaConsumer}
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer

import scala.annotation.tailrec
import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.reflect.ClassTag
import scala.collection.JavaConversions._
import scala.concurrent.duration._

object KafkaTestHelpers {
  def withThrowawayConsumerFor[E1: SchemaFor: FromRecord: ClassTag, R](t1: Topic[E1])(
      f: JKafkaConsumer[String, Option[E1]] => R): R = {
    val consumer = t1.consumer
    try {
      f(consumer)
    } finally {
      consumer.close()
    }
  }

  implicit class PimpedTestingTopic[E](topic: Topic[E]) {
    def consumer(implicit schemaFor: SchemaFor[E],
                 fromRecord: FromRecord[E],
                 classTag: ClassTag[E]): JKafkaConsumer[String, Option[E]] = {
      val chosenDeserializer =
        if (topic.useMagicByte)
          topic.deserializer
        else
          topic.deserializerNoMagicByte

      val initialConsumerSettings =
        Conf[String, Option[E]](new StringDeserializer,
                                chosenDeserializer,
                                topic.kafkaConfig.hosts,
                                UUID.randomUUID().toString)
      val consumerSettings = topic.kafkaConfig.ssl
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

      val consumer = KafkaConsumer(consumerSettings.withProperty("auto.offset.reset", "earliest"))
      consumer.subscribe(Seq(topic.name))
      consumer
    }

    def checkNoMessages(pollTime: FiniteDuration = 1.second)(implicit schemaFor: SchemaFor[E],
                                                             fromRecord: FromRecord[E],
                                                             classTag: ClassTag[E]) {
      withThrowawayConsumerFor(topic) { theConsumer =>
        val messages = theConsumer.poll(pollTime.toMillis).toSeq
        assert(messages.isEmpty)
      }
    }

    def pollConsumer(pollTime: FiniteDuration = 30.second,
                     noOfEventsExpected: Int = 1,
                     condition: E => Boolean = (_: E) => true)(implicit schemaFor: SchemaFor[E],
                                                               fromRecord: FromRecord[E],
                                                               classTag: ClassTag[E]): Seq[E] = {
      withThrowawayConsumerFor(topic) { theConsumer =>
        @tailrec
        def poll(deadline: Deadline, events: Seq[E]): Seq[E] = {
          if (deadline.hasTimeLeft) {
            val polledEvents: Seq[E] = theConsumer
              .poll(250)
              .records(topic.name)
              .flatMap(_.value())
              .filter(condition)
              .toSeq
            val eventsSoFar: Seq[E] = events ++ polledEvents
            eventsSoFar.length match {
              case n if n == noOfEventsExpected => eventsSoFar
              case exceeded if exceeded > noOfEventsExpected =>
                throw new Exception(s"Consumed more than $noOfEventsExpected events from ${topic.name}")
              case _ => poll(deadline, eventsSoFar)
            }
          } else
            throw new Exception("Events didn't appear within the timelimit")
        }

        poll(pollTime.fromNow, Nil)
      }
    }
  }
}
