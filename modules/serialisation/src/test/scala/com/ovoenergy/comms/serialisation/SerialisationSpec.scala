package com.ovoenergy.comms.serialisation

import com.ovoenergy.kafka.serialization.core.Format
import org.scalatest.{FlatSpec, Matchers}
import com.ovoenergy.kafka.serialization.core._
class SerialisationSpec extends FlatSpec with Matchers {

  behavior of "hackyFormatCheckingDeserializer"

  it should "handle a payload with the format byte correctly" in {
    val deserialiser = Serialisation.hackyFormatCheckingDeserializer(Format.AvroBinarySchemaId, constDeserializer(123))

    val res: Int = deserialiser.deserialize("this-doesnt-matter", Array[Byte](0, 2, 4))
    res shouldBe 123
  }

  it should "handle a payload without the format byte correctly" in {
    val deserialiser = Serialisation.hackyFormatCheckingDeserializer(Format.AvroBinarySchemaId, constDeserializer(123))

    val res: Int = deserialiser.deserialize("this-doesnt-matter", Array[Byte](2, 4))
    res shouldBe 123
  }

}
