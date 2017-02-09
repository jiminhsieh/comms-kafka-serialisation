package com.ovoenergy.comms.serialisation

import io.circe._
import io.circe.optics._
import shapeless._
import shapeless.labelled._

trait DecoderUtil {

  /**
    * Avro unions are encoded as a JSON object with a single field,
    * whose key is a type hint and whose value is the JSON encoding of the actual data.
    * e.g. `{ "string": "foo" }`.
    *
    * We ignore the type hint and descend into the nested value.
    */
  protected def getTheOnlyFieldOfJsonObject(json: Json): Option[Json] =
    JsonPath.root.each.json.headOption(json)

  implicit final val decodeCNil: Decoder[CNil] = new Decoder[CNil] {
    def apply(c: HCursor): Decoder.Result[CNil] = Left(DecodingFailure("CNil", c.history))
  }

}

trait LowPriorityDecoders extends DecoderUtil {

  trait CoproductDecoder[A] {
    def decoder: Decoder[A]
  }

  implicit final val coproductDecoderCNil: CoproductDecoder[CNil] = new CoproductDecoder[CNil] {
    val decoder = decodeCNil
  }

  implicit final def coproductDecoderCCons[L, R <: Coproduct](implicit
                                                              decodeL: Decoder[L],
                                                              decodeR: Lazy[CoproductDecoder[R]]
                                                             ): CoproductDecoder[L :+: R] = new CoproductDecoder[L :+: R] {
    val decoder: Decoder[L :+: R] = decodeL.map(Inl(_)).or(decodeR.value.decoder.map(Inr(_)))
  }

  implicit final def decodeCCons[L, R <: Coproduct](implicit
                                                    coproductDecoder: CoproductDecoder[L :+: R]
                                                   ): Decoder[L :+: R] = Decoder.instance[L :+: R] { (c: HCursor) =>
    getTheOnlyFieldOfJsonObject(c.value) match {
      case Some(json) => coproductDecoder.decoder.decodeJson(json)
      case None => Left(DecodingFailure(s"Error decoding Coproduct value", c.history))
    }
  }

}

object Decoders extends LowPriorityDecoders {

  implicit def decodeOption[T : Decoder]: Decoder[Option[T]] = Decoder.withReattempt[Option[T]] { (c: ACursor) =>
    /*
    An optional field could be None in two different ways:

    1. The field is present and explicitly set to null.
       This is how Avro encodes optional fields.

    2. The field is not present at all in the JSON,
       e.g. because the producer is using an older schema version that doesn't include the field
     */
    c.focus match {
      case Some(value) =>
        if (value.isNull) {
          Right(None)
        } else {
          getTheOnlyFieldOfJsonObject(value) match {
            case Some(json) => json.as[T].right.map(Some(_))
            case None => Left(DecodingFailure(s"Error decoding option value", c.history))
          }
        }
      case None =>
        Right(None)
    }
  }

  trait EnumDecoder[A] {
    def decoder: Decoder[A]
  }

  implicit final val enumDecoderCNil: EnumDecoder[CNil] = new EnumDecoder[CNil] {
    val decoder = decodeCNil
  }

  implicit def enumDecoderCCons[K <: Symbol, V, R <: Coproduct](implicit
                                                                wit: Witness.Aux[K],
                                                                emptyGen: LabelledGeneric.Aux[V, HNil],
                                                                edr: EnumDecoder[R]
                                                               ): EnumDecoder[FieldType[K, V] :+: R] =
    new EnumDecoder[FieldType[K, V] :+: R] {
      val decoder = Decoder.instance[FieldType[K, V] :+: R](c =>
        c.as[String] match {
          case Right(s) if s == wit.value.name => Right(Inl(field[K](emptyGen.from(HNil))))
          case Right(_) => edr.decoder.apply(c).right.map(Inr(_))
          case Left(err) => Left(DecodingFailure(s"Failed to decode enum ($err)", c.history))
        }
      )
    }

  implicit def decodeEnum[A, R <: Coproduct](implicit
                                             gen: LabelledGeneric.Aux[A, R],
                                             edr: EnumDecoder[R]
                                            ): Decoder[A] =
    Decoder.instance[A](c => edr.decoder(c).right.map(gen.from))

}
