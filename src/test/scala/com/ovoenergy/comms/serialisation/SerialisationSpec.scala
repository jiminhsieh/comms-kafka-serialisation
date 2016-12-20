package com.ovoenergy.comms.serialisation

import org.scalatest.{FlatSpec, Matchers}
import com.sksamuel.avro4s._

case class Thing(a: Int, b: String)

class SerialisationSpec extends FlatSpec with Matchers {

  "deserialization" should "be resilient" in {
    val deserializer = Serialisation.avroDeserializer[Thing]
    deserializer.deserialize("my-topic", "yolo".getBytes) should be(None)
  }

}
