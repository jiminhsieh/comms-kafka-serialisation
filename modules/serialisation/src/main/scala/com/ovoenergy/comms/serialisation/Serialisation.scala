package com.ovoenergy.comms.serialisation

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import java.util
import com.ovoenergy.comms.serialisation.Retry.RetryConfig
import com.ovoenergy.kafka.serialization.avro.{JerseySchemaRegistryClient, SchemaRegistryClientSettings}
import com.sksamuel.avro4s._
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.slf4j.LoggerFactory
import com.ovoenergy.kafka.serialization.avro4s._
import com.ovoenergy.kafka.serialization.core.Format.AvroBinarySchemaId
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import com.ovoenergy.kafka.serialization.core._
import cats.implicits._
import scala.reflect.ClassTag
import scala.util.Try

object Serialisation {

  val log = LoggerFactory.getLogger(getClass)

  def avroDeserializer[T: SchemaFor: FromRecord: ClassTag]: Deserializer[Option[T]] =
    new Deserializer[Option[T]] {
      override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

      override def close(): Unit = {}

      override def deserialize(topic: String, data: Array[Byte]): Option[T] = {
        val bais = new ByteArrayInputStream(data)
        val result = for {
          input  <- Try(AvroJsonInputStream[T](bais))
          entity <- input.singleEntity
        } yield entity

        result.failed.foreach { e =>
          val className = implicitly[ClassTag[T]].runtimeClass.getSimpleName
          log.warn(
            s"""Skippping event because it could not be deserialized to $className
               |Event:
               |${new String(data, StandardCharsets.UTF_8)}
            """.stripMargin,
            e
          )
        }

        result.toOption
      }
    }

  def avroSerializer[T: SchemaFor: ToRecord]: Serializer[T] =
    new Serializer[T] {
      override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

      override def close(): Unit = {}

      override def serialize(topic: String, data: T): Array[Byte] = {
        val baos   = new ByteArrayOutputStream()
        val output = AvroJsonOutputStream[T](baos)
        output.write(data)
        output.close()
        baos.toByteArray
      }
    }

  def avroBinarySchemaRegistryDeserializer[T: FromRecord: SchemaFor: ClassTag](
      schemaRegistryClientSettings: SchemaRegistryClientSettings,
      topic: String,
      schemaRegistryConfig: RetryConfig): Either[Retry.Failed, Deserializer[Option[T]]] = {
    val schemaRegistryClient                   = JerseySchemaRegistryClient(schemaRegistryClientSettings)
    val baseDeserializer                       = avroBinarySchemaIdDeserializer[T](schemaRegistryClient, isKey = false)
    val formattedDeserializer: Deserializer[T] = formatCheckingDeserializer(AvroBinarySchemaId, baseDeserializer)

    registerSchema[T](schemaRegistryClient, topic, schemaRegistryConfig).map { _ =>
      new Deserializer[Option[T]] {
        override def configure(configs: util.Map[String, _], isKey: Boolean) {
          formattedDeserializer.configure(configs, isKey)
        }

        override def close(): Unit = formattedDeserializer.close()

        override def deserialize(topic: String, data: Array[Byte]): Option[T] = {
          val result = Try {
            formattedDeserializer.deserialize(topic, data)
          }

          result.failed.foreach { e =>
            val className = implicitly[ClassTag[T]].runtimeClass.getSimpleName
            log.warn(
              s"""Skippping event because it could not be deserialized to $className

                     |Event:
                     |${new String(data, StandardCharsets.UTF_8)}
            """.stripMargin,
              e
            )
          }
          result.toOption
        }
      }
    }
  }

  def avroBinarySchemaRegistrySerializer[T: ToRecord: SchemaFor](
      schemaRegistryClientSettings: SchemaRegistryClientSettings,
      topic: String,
      retryConfig: RetryConfig) = {
    val schemaRegistryClient = JerseySchemaRegistryClient(schemaRegistryClientSettings)
    val serializer           = avroBinarySchemaIdSerializer[T](schemaRegistryClient, isKey = false)

    registerSchema[T](schemaRegistryClient, topic, retryConfig).map { _ =>
      formatSerializer(AvroBinarySchemaId, serializer)
    }
  }

  private def registerSchema[T](schemaRegistryClient: SchemaRegistryClient, topic: String, retryConfig: RetryConfig)(
      implicit sf: SchemaFor[T]): Either[Retry.Failed, Retry.Succeeded[Int]] = {

    val schema                = sf.apply()
    val trySchemaRegistration = () => Try(schemaRegistryClient.register(s"$topic-value", schema))

    val onFailure = { (e: Throwable) =>
      log.debug(s"Schema registration attempt was unsuccessful. ${e.getMessage}")
    }

    Retry.retry(retryConfig, onFailure)(trySchemaRegistration)
  }

}
