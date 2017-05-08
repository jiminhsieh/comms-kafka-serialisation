package com.ovoenergy.comms.serialisation

import java.time.Instant

import org.scalatest.{FlatSpec, Matchers}
import io.circe._
import io.circe.generic.auto._
import shapeless._
import com.sksamuel.avro4s._
import org.apache.avro.Schema
import org.apache.avro.Schema.Field
import org.apache.avro.generic.GenericData.EnumSymbol
import com.ovoenergy.comms.model.ComposedEmail

import scala.reflect.ClassTag

class SerialisationSpec extends FlatSpec with Matchers {

  case class Thing(a: Int, b: String)

  case class Parent(a: String, b: Child, c: Int)
  case class Child(x: String)

  case class WithOptionalString(theOption: Option[String])

  case class OptionalCaseClass(a: String, b: Int)
  case class WithOptionalCaseClass(theOption: Option[OptionalCaseClass])

  case class Recursive(a: String, recursive: Option[Recursive])
  object Recursive {
    implicit val schemaFor = SchemaFor[Recursive]
  }

  sealed trait Enum
  case object EnumA extends Enum
  case object EnumB extends Enum
  case class WithEnum(e: Enum)

  object Enum {
    implicit object EnumToSchema extends ToSchema[Enum] {
      override val schema: Schema = Schema.createEnum(
        "Enum",
        "an enum",
        "com.ovoenergy.comms.serialisation",
        java.util.Arrays.asList("EnumA", "EnumB")
      )
    }

    implicit object EnumToValue extends ToValue[Enum] {
      override def apply(value: Enum): EnumSymbol =
        new EnumSymbol(null, value.toString)
    }

    implicit object EnumFromValue extends FromValue[Enum] {
      override def apply(value: Any, field: Field): Enum = value.toString match {
        case "EnumA" => EnumA
        case "EnumB" => EnumB
      }
    }
  }

  type TemplateDataType = String :+: Seq[TemplateData] :+: Map[String, TemplateData] :+: CNil
  case class TemplateData(value: TemplateDataType)
  object TemplateData {
    implicit val schemaFor = SchemaFor[TemplateData]
  }

  case class FakeMessage(metadata: Recursive, templateData: TemplateData, random: String)

  behavior of "deserialization"

  it should "be resilient" in {
    val deserializer = Serialisation.avroDeserializer[Thing]
    deserializer.deserialize("my-topic", "yolo".getBytes) should be(None)
  }

  it should "deserialize extra fields to case class with variable types" in {
    val jsonString =
      """
        |{
        |  "a": "aString",
        |  "b": {
        |    "x": "vaString",
        |    "extra": "extraString"
        |  },
        |  "c": 1
        |}
      """.stripMargin


    val deserializer = Serialisation.avroDeserializer[Parent]
    deserializer.deserialize("my-topic", jsonString.getBytes) shouldBe Some(Parent("aString", Child("vaString"), 1))
  }

  it should "handle extra fields in example message" in {
    import Codecs._

    val json =
      """
        |{
        |  "metadata": {"a":"parent", "extra": "extraVal","recursive":{"com.ovoenergy.comms.serialisation.Recursive":{"a":"child","extra": "extraVal", "recursive":null}}, "extra": "extraVal"},
        |  "templateData": {"value":{"map":{"aString":{"value":{"string":"stringValue1"}},"aStringSeq":{"value":{"array":[{"value":{"string":"stringSeq1"}},{"value":{"string":"stringSeq2"}}]}},"aMap":{"value":{"map":{"mapKey":{"value":{"string":"mapValue1"}}}}},"aSeqOfMap":{"value":{"array":[{"value":{"map":{"seqMapKey":{"value":{"string":"seqMapValue1"}}}}}]}}}}},
        |  "random": "whatevs",
        |  "extra": "extraVal"
        |}
      """.stripMargin

    val expected = {
      val metadataChild = Recursive("child", None)
      val metadataParent = Recursive("parent", Some(metadataChild))

      val map = Map("mapKey" -> TemplateData(Coproduct[TemplateDataType]("mapValue1")))
      val seqString = Seq(TemplateData(Coproduct[TemplateDataType]("stringSeq1")), TemplateData(Coproduct[TemplateDataType]("stringSeq2")))
      val seqMap = Seq(
        TemplateData(Coproduct[TemplateDataType](Map("seqMapKey" -> TemplateData(Coproduct[TemplateDataType]("seqMapValue1")))))
      )
      val templateData = Map(
        "aString" -> TemplateData(Coproduct[TemplateDataType]("stringValue1")),
        "aStringSeq" -> TemplateData(Coproduct[TemplateDataType](seqString)),
        "aMap" -> TemplateData(Coproduct[TemplateDataType](map)),
        "aSeqOfMap" -> TemplateData(Coproduct[TemplateDataType](seqMap))
      )
      FakeMessage(
        metadataParent,
        TemplateData(Coproduct[TemplateDataType](templateData)),
        "whatevs"
      )
    }

    Serialisation.avroDeserializer[FakeMessage].deserialize("my-topic", json.getBytes()) shouldBe Some(expected)
  }

  it should "deserialize an Option as None if the field is not present" in {
    import Codecs._

    // This json does not include the optional 'expireAt' field
    val jsonString =
			"""
			{
				"metadata":{
					"createdAt":"2017-02-07T16:57:21.492Z",
					"eventId":"b46c6beb-f23c-47e2-870e-c71035d8013f",
					"customerId":"canary-customer",
					"traceToken":"91876e68-e4fd-4cd0-a4b4-5b496745643c",
					"commManifest":{"commType":"Service","name":"canary","version":"0.2"},
					"friendlyDescription":"canary event",
					"source":"comms-composer",
					"canary":true,
					"sourceMetadata":null,
					"triggerSource":"canary trigger Lambda"
				},
				"internalMetadata":{"internalTraceToken":"49f343ee-c6f9-45b1-ab44-71a3321f0812"},
				"sender":"Ovo Energy <no-reply@ovoenergy.com>",
				"recipient":"ovo.comms.canary@gmail.com",
				"subject":"Canary test email (91876e68-e4fd-4cd0-a4b4-5b496745643c)\n",
				"htmlBody":"here's some html",
				"textBody":null
			}
			"""

    val composedEmail = Serialisation.avroDeserializer[ComposedEmail].deserialize("my-topic", jsonString.getBytes)
    composedEmail shouldBe 'defined
    composedEmail.get.htmlBody shouldBe "here's some html"
    composedEmail.get.expireAt shouldBe None
  }

  behavior of "round trip"

  it should "handle a case class containing case classes" in {
    val caseClass = Parent("aString", Child("vaString"), 1)

    checkRoundTrip(caseClass)
  }

  it should "handle a TemplateData containing a Seq" in {
    import Codecs._

    val seq = Seq(TemplateData(Coproduct[TemplateDataType]("stringValue1")))
    val templateDataSeq = TemplateData(Coproduct[TemplateDataType](seq))

    checkRoundTrip(templateDataSeq)
  }

  it should "handle a TemplateData containing a Map" in {
    import Codecs._

    val map = Map("keyString" -> TemplateData(Coproduct[TemplateDataType]("stringValue1")))
    val templateDataMap = TemplateData(Coproduct[TemplateDataType](map))

    checkRoundTrip(templateDataMap)
  }

  it should "handle a TemplateData containing a String" in {
    import Codecs._

    val templateDataString = TemplateData(Coproduct[TemplateDataType]("stringValue1"))

    checkRoundTrip(templateDataString)
  }

  it should "handle a TemplateData containing a complex structure" in {
    import Codecs._

    val map = Map("mapKey" -> TemplateData(Coproduct[TemplateDataType]("mapValue1")))
    val seqString = Seq(TemplateData(Coproduct[TemplateDataType]("stringSeq1")), TemplateData(Coproduct[TemplateDataType]("stringSeq2")))
    val seqMap = Seq(
      TemplateData(Coproduct[TemplateDataType](Map("seqMapKey" -> TemplateData(Coproduct[TemplateDataType]("seqMapValue1")))))
    )
    val templateData = Map(
      "aString" -> TemplateData(Coproduct[TemplateDataType]("stringValue1")),
      "aStringSeq" -> TemplateData(Coproduct[TemplateDataType](seqString)),
      "aMap" -> TemplateData(Coproduct[TemplateDataType](map)),
      "aSeqOfMap" -> TemplateData(Coproduct[TemplateDataType](seqMap))
    )

    val templateDataComplex = TemplateData(Coproduct[TemplateDataType](templateData))

    checkRoundTrip(templateDataComplex)
  }

  it should "handle Option of String (Some)" in {
    import Codecs._

    val withOption = WithOptionalString(Some("stringValue"))

    checkRoundTrip(withOption)
  }

  it should "handle Option of String (None)" in {
    import Codecs._

    val withOption = WithOptionalString(None)

    checkRoundTrip(withOption)
  }

  it should "handle Option of case class (Some)" in {
    import Codecs._

    val withOption = WithOptionalCaseClass(Some(OptionalCaseClass("stringValue", 1)))

    checkRoundTrip(withOption)
  }

  it should "handle Option of case class (None)" in {
    import Codecs._

    val withOption = WithOptionalCaseClass(None)

    checkRoundTrip(withOption)
  }

  it should "handle Option of some complex structure" in {
    import Codecs._

    case class WithOption(theOption: Option[Seq[Map[String, OptionalCaseClass]]])

    val someMap1 = Map("key1-1" -> OptionalCaseClass("stringValue1-1", 1), "key1-2" -> OptionalCaseClass("stringValue1-2", 1))
    val someMap2 = Map("key1-2" -> OptionalCaseClass("stringValue1-2", 1), "key2-2" -> OptionalCaseClass("stringValue2-2", 1))
    val withOption = WithOption(Some(Seq(someMap1, someMap2)))

    checkRoundTrip(withOption)
  }

  it should "handle recursive types" in {
    import Codecs._

    val child = Recursive("child", None)
    val parent = Recursive("parent", Some(child))

    checkRoundTrip(parent)
  }

  it should "handle enums" in {
    import Codecs._

    checkRoundTrip(WithEnum(EnumA))
  }

  it should "handle an Instant" in {
    import io.circe.generic.auto._
    import Codecs._

    object WithInstant {
      implicit val schema = SchemaFor[WithInstant]
    }
    case class WithInstant(anInstant: Instant)
    checkRoundTrip(WithInstant(Instant.now))
  }

  def checkRoundTrip[A: Decoder: SchemaFor: ToRecord: ClassTag](original: A): Unit = {
    val serialized = Serialisation.avroSerializer[A].serialize("my-topic", original)
    val deserialized = Serialisation.avroDeserializer[A].deserialize("my-topic", serialized)
    deserialized shouldBe Some(original)
  }
}
