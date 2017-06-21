package com.ovoenergy.comms.serialisation

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import java.util

import com.ovoenergy.kafka.serialization.avro.{Authentication, JerseySchemaRegistryClient, SchemaRegistryClientSettings}
import com.sksamuel.avro4s._
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.slf4j.LoggerFactory
import com.ovoenergy.kafka.serialization.avro4s._

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
        val input = AvroJsonInputStream[T](bais)
        val result = input.singleEntity

        result.failed.foreach { e =>
          val className = implicitly[ClassTag[T]].runtimeClass.getSimpleName
          log.warn(
            s"""Skippping event because it could not be deserialized to $className
               |Event:
               |${new String(data, StandardCharsets.UTF_8)}
            """.stripMargin,
            e)
        }

        result.toOption
      }
    }

  /**
    * This deserializer should only be used for TriggeredV2 messages.
    * It is generic only to avoid a dependency on the comms-kafka-messages library.
    */
  def hackyAvroDeserializerForTriggeredV2[T: SchemaFor: FromRecord: ClassTag]: Deserializer[Option[T]] = {
    import io.circe._
    import io.circe.parser._
    val nulledOptionalFields = Json.obj(
      "deliverAt" -> Json.Null,
      "expireAt" -> Json.Null,
      "preferredChannels" -> Json.Null
    )
    def addMissingOptionalFields(data: Array[Byte]): Either[ParsingFailure, Array[Byte]] = {
      parse(new String(data, StandardCharsets.UTF_8)).right.map { originalJson =>
        // Note: the order is important when calling `deepMerge`.
        // Values in `originalJson` take precedence over values in `nulledOptionalFields`.
        val fixedJson = nulledOptionalFields.deepMerge(originalJson)
        fixedJson.noSpaces.getBytes(StandardCharsets.UTF_8)
      }
    }

    new Deserializer[Option[T]] {
      override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}
      override def close(): Unit = {}
      override def deserialize(topic: String, data: Array[Byte]): Option[T] = {
        addMissingOptionalFields(data) match {
          case Left(err) =>
            log.warn(
              s"""Skippping event because it could not be deserialized to JSON using circe
                 |Event:
                 |${new String(data, StandardCharsets.UTF_8)}
            """.stripMargin,
              err)
            None
          case Right(fixedBytes) =>
            val bais = new ByteArrayInputStream(fixedBytes)
            val input = AvroJsonInputStream[T](bais)
            val result = input.singleEntity

            result.failed.foreach { e =>
              val className = implicitly[ClassTag[T]].runtimeClass.getSimpleName
              log.warn(
                s"""Skippping event because it could not be deserialized to $className
                   |Event:
                   |${new String(data, StandardCharsets.UTF_8)}
                   |After adding missing optional fields:
                   |${new String(fixedBytes, StandardCharsets.UTF_8)}
            """.stripMargin,
                e)
            }

            result.toOption
        }
      }
    }
  }

  def avroSerializer[T: SchemaFor: ToRecord]: Serializer[T] =
    new Serializer[T] {
      override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}
      override def close(): Unit = {}
      override def serialize(topic: String, data: T): Array[Byte] = {
        val baos = new ByteArrayOutputStream()
        val output = AvroJsonOutputStream[T](baos)
        output.write(data)
        output.close()
        baos.toByteArray
      }
    }


  def avroBinarySchemaRegistryDeserializer[T: FromRecord: SchemaFor: ClassTag](schemaRegistrySettings: SchemaRegistryClientSettings, topic: String) = {
    val baseDeserializer = avroBinarySchemaIdDeserializer[T](schemaRegistrySettings, isKey = false)
    registerSchema[T](schemaRegistrySettings, topic)
    new Deserializer[Option[T]]{
      override def configure(configs: util.Map[String, _], isKey: Boolean): Unit =
        baseDeserializer.configure(configs, isKey)
      override def close(): Unit = baseDeserializer.close()
      override def deserialize(topic: String, data: Array[Byte]): Option[T] = {

        val result = Try { baseDeserializer.deserialize(topic, data) }
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

  def avroBinarySchemaRegistrySerializer[T: ToRecord: SchemaFor](schemaRegistryClientSettings: SchemaRegistryClientSettings, topic: String) = {
    registerSchema[T](schemaRegistryClientSettings, topic)
    avroBinarySchemaIdSerializer[T](schemaRegistryClientSettings, isKey = false)
  }

  private def registerSchema[T: SchemaFor](schemaRegistryClientSettings: SchemaRegistryClientSettings, topic: String)= {
    val schemaRegistryClient = JerseySchemaRegistryClient(schemaRegistryClientSettings)
    val schemaFor: SchemaFor[T] = implicitly[SchemaFor[T]]
    val schema = schemaFor.apply()
    schemaRegistryClient.register(s"$topic-value", schema)
  }
}
