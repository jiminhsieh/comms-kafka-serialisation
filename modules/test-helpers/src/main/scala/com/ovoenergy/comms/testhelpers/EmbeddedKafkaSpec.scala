package com.ovoenergy.comms.testhelpers

import java.util.Properties

import com.typesafe.config.ConfigFactory
import io.confluent.kafka.schemaregistry.rest.{SchemaRegistryConfig, SchemaRegistryRestApplication}
import kafka.admin.AdminUtils
import kafka.server.KafkaServer
import kafka.utils.ZkUtils
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.zookeeper.server.ServerCnxnFactory
import org.eclipse.jetty.server.Server
import org.scalatest.{BeforeAndAfterAll, Suite}
import scala.collection.JavaConversions._

import scala.reflect.io.Directory

object EmbeddedSchemaRegistry {
  private var serverMaybe: Option[Server] = None
  def start()(implicit config: EmbeddedKafkaConfig): Unit = {
    val props = new Properties()

    props.setProperty("kafkastore.connection.url", s"localhost:${config.zooKeeperPort}")
    val schemaRegistryConfig = new SchemaRegistryConfig(props)
    val app                  = new SchemaRegistryRestApplication(schemaRegistryConfig)
    val server: Server       = app.createServer
    server.start()
    serverMaybe = Some(server)
  }

  def stop() = {
    serverMaybe.foreach(_.stop())
  }
}

trait EmbeddedKafkaSpec extends EmbeddedKafka with BeforeAndAfterAll { this: Suite =>

  private def cleanLogs(directories: Directory*): Unit = {
    directories.foreach(_.deleteRecursively())
  }
  private val zkLogsDirAiven     = Directory.makeTemp("zookeeper-logs-aiven")
  private val kafkaLogsDirAiven  = Directory.makeTemp("kafka-logs-aiven")

  val aivenConfig                                = EmbeddedKafkaConfig(6004, 6001)
  var aivenZookeeper: Option[ServerCnxnFactory]  = None
  var aivenKafkaBroker: Option[KafkaServer]      = None

  override protected def beforeAll() {

    aivenZookeeper = Some(startZooKeeper(aivenConfig.zooKeeperPort, zkLogsDirAiven))
    aivenKafkaBroker = Some(startKafka(aivenConfig, kafkaLogsDirAiven))

    EmbeddedSchemaRegistry.start()

    makeTopicsInConfig("aiven")(aivenConfig)
  }

  override protected def afterAll() {

    aivenKafkaBroker.foreach(_.shutdown())
    aivenKafkaBroker.foreach(_.awaitShutdown())
    aivenZookeeper.foreach(_.shutdown())

    EmbeddedSchemaRegistry.stop()

    cleanLogs(zkLogsDirAiven, kafkaLogsDirAiven)
  }

  private def makeTopicsInConfig(kafkaCluster: String)(implicit embeddedKafkaConfig: EmbeddedKafkaConfig) {
    val topics = ConfigFactory
      .load()
      .getConfig(s"kafka.$kafkaCluster.topics")
      .entrySet()
      .map(_.getValue.render.replace("\"", ""))

    val zkUtils = ZkUtils(s"localhost:${embeddedKafkaConfig.zooKeeperPort}",
                          zkSessionTimeoutMs,
                          zkConnectionTimeoutMs,
                          zkSecurityEnabled)

    topics
      .filterNot(t => AdminUtils.topicExists(zkUtils, t))
      .foreach(t => {
        AdminUtils.createTopic(zkUtils, t, 1, 1, new Properties)
      })
  }
}
