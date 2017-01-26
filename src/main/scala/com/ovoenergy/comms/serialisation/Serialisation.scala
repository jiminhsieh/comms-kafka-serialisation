package com.ovoenergy.comms.serialisation

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util

import com.sksamuel.avro4s._
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.slf4j.LoggerFactory
import io.circe._
import io.circe.parser._
import cats.syntax.either._

import scala.reflect.ClassTag

object Serialisation {

  val log = LoggerFactory.getLogger(getClass)

  def avroDeserializer[T: Decoder: ClassTag]: Deserializer[Option[T]] =
    new Deserializer[Option[T]] {
      override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}
      override def close(): Unit = {}
      override def deserialize(topic: String, data: Array[Byte]): Option[T] = {
        val string = new String(data, StandardCharsets.UTF_8)
        val result = decode[T](string)

        result.left.foreach { e =>
          val className = implicitly[ClassTag[T]].runtimeClass.getSimpleName
          log.warn(
            s"""Skippping event because it could not be deserialized to $className
               |Event:
               |$string
            """.stripMargin,
            e)
        }

        result.toOption
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



}
