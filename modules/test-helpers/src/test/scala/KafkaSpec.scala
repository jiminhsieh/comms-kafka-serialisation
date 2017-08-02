import com.ovoenergy.comms.helpers.{Kafka, Topic}
import com.ovoenergy.comms.testhelpers._
import com.sksamuel.avro4s.{FromRecord, SchemaFor, ToRecord}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalacheck.Arbitrary
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}

import scala.reflect.ClassTag

class KafkaSpec extends FlatSpec with EmbeddedKafkaSpec with Matchers with ScalaFutures {

  //Import magic for generating arbitraries, traversing HLists, serializing and deserializing stuff
  import ArbGenerator._
  import TraverseTopicList._
  import com.ovoenergy.comms.serialisation.Codecs._
  import shapeless._
  import org.scalacheck.Shapeless._

  //Pimp the topics
  import KafkaTestHelpers._

  implicit val config: Config          = ConfigFactory.load()
  override implicit val patienceConfig = PatienceConfig(scaled(Span(10, Seconds)))

  val visitor = new TopicListVisitor {
    override def apply[E: SchemaFor: Arbitrary: ToRecord: FromRecord: ClassTag](topic: Topic[E]): Unit = {
      it should s"Send and receive ${topic.name}" in {
        val e = generate[E]

        val pub = topic.publisher
        topic.checkNoMessages()
        pub(e).futureValue
        topic.pollConsumer() shouldBe Seq(e)
      }
    }
  }

  behavior of "legacy cluster"
  TraverseTopicList(Kafka.legacy.allTopics, visitor)

  behavior of "aiven cluster"
  TraverseTopicList(Kafka.aiven.allTopics, visitor)
}
