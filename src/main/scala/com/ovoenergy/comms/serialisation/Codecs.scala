package com.ovoenergy.comms.serialisation

import java.time.Instant

import com.sksamuel.avro4s.{FromValue, ToSchema, ToValue}
import org.apache.avro.{LogicalTypes, Schema}

object Codecs {

  implicit object InstantToSchema extends ToSchema[Instant] {
    override val schema: Schema = {
      val s = Schema.create(Schema.Type.LONG)
      LogicalTypes.timestampMillis().addToSchema(s)
    }
  }

  implicit object InstantToValue extends ToValue[Instant] {
    override def apply(value: Instant): Long = value.toEpochMilli
  }

  implicit object InstantFromValue extends FromValue[Instant] {
    override def apply(value: Any, field: Schema.Field) = Instant.ofEpochMilli(value.toString.toLong)
  }


}
