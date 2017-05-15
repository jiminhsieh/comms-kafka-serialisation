package com.ovoenergy.comms.serialisation

import java.nio.file.{Files, Paths}

import com.ovoenergy.comms.model._
import org.scalatest.{FlatSpec, Matchers}

class TriggeredV2DeserialisationSpec extends FlatSpec with Matchers {

  val deserializer = Serialisation.hackyAvroDeserializerForTriggeredV2[TriggeredV2]

  it should "deserialise a TriggeredV2 event that does not include any of the optional fields" in {
    val bytes = Files.readAllBytes(Paths.get("src/test/resources/TriggeredV2-without-optional-fields.json"))
    val result = deserializer.deserialize("", bytes).get
    result.deliverAt shouldBe None
    result.expireAt shouldBe None
    result.preferredChannels shouldBe None
  }

  it should "deserialise a TriggeredV2 event that includes some of the optional fields" in {
    val bytes = Files.readAllBytes(Paths.get("src/test/resources/TriggeredV2-with-some-optional-fields.json"))
    val result = deserializer.deserialize("", bytes).get
    result.deliverAt shouldBe Some("2018-01-01T12:34:56.000Z")
    result.expireAt shouldBe None
    result.preferredChannels shouldBe None
  }

  it should "deserialise a TriggeredV2 event that includes all of the optional fields" in {
    val bytes = Files.readAllBytes(Paths.get("src/test/resources/TriggeredV2-with-all-optional-fields.json"))
    val result = deserializer.deserialize("", bytes).get
    result.deliverAt shouldBe Some("2018-01-01T12:34:56.000Z")
    result.expireAt shouldBe Some("2018-01-02T12:34:56.000Z")
    result.preferredChannels shouldBe Some(List(Email, SMS))
  }

}
