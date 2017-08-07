package com.ovoenergy.comms.testhelpers

import java.nio.file.Paths
import java.util
import java.util.UUID

import cakesolutions.kafka.KafkaConsumer
import cakesolutions.kafka.KafkaConsumer.Conf
import com.ovoenergy.comms.helpers.Topic
import com.sksamuel.avro4s.{FromRecord, SchemaFor, ToRecord}
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.{OffsetAndMetadata, KafkaConsumer => JKafkaConsumer}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.ExecutionContext.Implicits.global
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.reflect.ClassTag
import scala.collection.JavaConversions._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object KafkaTestHelpers {
  def loggerName: String = getClass.getSimpleName.reverse.dropWhile(_ == '$').reverse

  lazy val log = LoggerFactory.getLogger(loggerName)

  def withThrowawayConsumerFor[E1: SchemaFor : FromRecord : ClassTag, R](topic: Topic[E1])(
    f: JKafkaConsumer[String, Option[E1]] => R): R = {
    val consumer = topic.consumer(fromBeginning = false)
    consumer.poll(0)
    consumer.partitionsFor(topic.name) // as poll doesn't honour the timeout, forcing the consumer to fail here.
    log.info(
      s"""Created consumer for ${topic.name}, subscription: ${
        consumer
          .assignment()
          .map(tp => tp -> consumer.position(tp))
          .map(s => s"${s._1} @ ${s._2}")
      }"""
    )
    try {
      f(consumer)
    } finally {
      consumer.close()
    }
  }

  def withThrowawayConsumerFor[E1: SchemaFor : FromRecord : ClassTag, E2: SchemaFor : FromRecord : ClassTag, R](
                                                                                                                 t1: Topic[E1],
                                                                                                                 t2: Topic[E2])(f: (JKafkaConsumer[String, Option[E1]], JKafkaConsumer[String, Option[E2]]) => R): R = {
    withThrowawayConsumerFor(t1) { c1 =>
      withThrowawayConsumerFor(t2) { c2 =>
        f(c1, c2)
      }
    }
  }

  implicit class PimpedTestingTopic[E](topic: Topic[E]) {
    def consumer(fromBeginning: Boolean = true)(implicit schemaFor: SchemaFor[E],
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

      val resetOffset = if (fromBeginning) "earliest" else "latest"
      val consumer = KafkaConsumer(consumerSettings.withProperty("auto.offset.reset", resetOffset))
      consumer.subscribe(Seq(topic.name))
      consumer
    }

    def pollConsumer(pollTime: FiniteDuration = 30.second,
                     noOfEventsExpected: Int = 1,
                     fromBeginning: Boolean = true,
                     condition: E => Boolean = (_: E) => true)(implicit schemaFor: SchemaFor[E],
                                                               fromRecord: FromRecord[E],
                                                               classTag: ClassTag[E]): Seq[E] = {
      val theConsumer = topic.consumer(fromBeginning = fromBeginning)
      try {
        theConsumer.pollFor(pollTime, noOfEventsExpected, condition)
      } finally {
        theConsumer.close()
      }
    }

    def publishOnce(event: E, timeout: Duration = 5.seconds)(implicit schemaFor: SchemaFor[E], toRecord: ToRecord[E], classTag: ClassTag[E]) = {
      val localProducer = topic.producer
      try {
        val future = localProducer.send(new ProducerRecord[String, E](topic.name, event))
        // Enforce blocking behaviour
        Await.result(future, timeout)
      } catch {
        case e => {
          throw new Exception(s"Failed to publish message to topic ${topic.name} with error $e")
        }
      } finally{
        localProducer.close()
      }
    }
  }

  implicit class PimpedKafkaConsumer[K, V](theConsumer: JKafkaConsumer[K, Option[V]]) {
    val subscription: util.Set[String] = theConsumer.subscription()

    require(
      subscription.size() == 1,
      s"""Only works with a consumer which is subscribed to exactly one topic, actually subscribed to ${
        theConsumer.subscription
          .mkString(",")
      }"""
    )
    val topicName: String = subscription.head

    def pollFor(pollTime: FiniteDuration = 30.second,
                noOfEventsExpected: Int = 1,
                condition: V => Boolean = (_: V) => true) = {
      @tailrec
      def poll(deadline: Deadline, events: Seq[V]): Seq[V] = {
        if (deadline.hasTimeLeft) {
          val records = theConsumer
            .poll(250)
            .records(topicName)

          if (records.nonEmpty) {
            log.debug(s"""Polled topic $topicName and found offsets of: ${records.map(_.offset()).mkString(", ")}""")
          }

          val polledEvents: Seq[V] =
            records
              .flatMap(_.value())
              .filter(condition)
              .toSeq

          records.lastOption.foreach { record =>
            val topicPartition = new TopicPartition(record.topic(), record.partition())
            val offsetAndMetadata = new OffsetAndMetadata(record.offset() + 1)
            log.debug(
              s"""Committed offset of ${offsetAndMetadata.offset()} to topic ${
                topicPartition
                  .topic()
              }, partition ${topicPartition.partition()}""")
            theConsumer.commitSync(Map(topicPartition -> offsetAndMetadata))
          }

          val eventsSoFar: Seq[V] = events ++ polledEvents
          eventsSoFar.length match {
            case n if n == noOfEventsExpected => eventsSoFar
            case exceeded if exceeded > noOfEventsExpected => {
              val events = eventsSoFar.mkString("[", "\n", "]")
              throw new Exception(s"Consumed more than $noOfEventsExpected events from $topicName.  Received: $events")
            }
            case _ => poll(deadline, eventsSoFar)
          }
        } else
          throw new Exception(s"Events didn't appear within the ${pollTime.toString()} timelimit")
      }

      poll(pollTime.fromNow, Nil)
    }

    def checkNoMessages(pollTime: FiniteDuration = 1.second) {
      @tailrec
      def poll(deadline: Deadline, events: Seq[V]): Seq[V] = {
        if (events.nonEmpty) {
          throw new Exception(s"${events.length} messages were consumed")
        } else if (deadline.hasTimeLeft) {
          val polledEvents: Seq[V] = theConsumer
            .poll(250)
            .records(topicName)
            .flatMap(_.value())
            .toSeq
          val eventsSoFar: Seq[V] = events ++ polledEvents
          poll(deadline, eventsSoFar)
        } else events
      }

      val messages = theConsumer.poll(pollTime.toMillis).toSeq
      assert(messages.isEmpty)
    }
  }

}
