package com.ovoenergy.comms.testhelpers

import com.ovoenergy.comms.helpers.Topic
import com.sksamuel.avro4s.{FromRecord, SchemaFor, ToRecord}
import org.scalacheck.Arbitrary
import shapeless._
import scala.reflect.ClassTag

trait TopicListVisitor {
  def apply[E: SchemaFor: Arbitrary: ToRecord: FromRecord: ClassTag](topic: Topic[E]): Unit
}

trait TraverseTopicList[T] {
  def traverse(t: T, visitor: TopicListVisitor): Unit
}

object TraverseTopicList {
  def apply[L <: HList](list: L, visitor: TopicListVisitor)(implicit traverser: TraverseTopicList[L]): Unit =
    traverser.traverse(list, visitor)

  implicit def HNilTraverser = new TraverseTopicList[HNil] {
    override def traverse(t: HNil, visitor: TopicListVisitor) {}
  }

  implicit def HListTraverser[E: SchemaFor: Arbitrary: ToRecord: FromRecord: ClassTag, InT <: HList](
      implicit tailTraverser: TraverseTopicList[InT]): TraverseTopicList[Topic[E] :: InT] = {
    new TraverseTopicList[Topic[E] :: InT] {
      override def traverse(l: Topic[E] :: InT, visitor: TopicListVisitor): Unit = {
        visitor(l.head)
        tailTraverser.traverse(l.tail, visitor)
      }
    }
  }
}
