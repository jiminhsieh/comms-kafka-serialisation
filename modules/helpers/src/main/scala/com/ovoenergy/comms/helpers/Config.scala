package com.ovoenergy.comms.helpers

import com.ovoenergy.comms.serialisation.Retry.RetryConfig
import com.typesafe.config.{Config, ConfigObject, ConfigValue}
import pureconfig._
import pureconfig.syntax._
import cats.instances.either._
import cats.syntax.either._
import pureconfig.error.ConfigReaderFailures

import scala.concurrent.duration.FiniteDuration

case class KafkaRootConfig(kafka: KafkaConfig)
case class StoreConfig(location: String, password: String)
case class SSLConfig(keystore: StoreConfig, truststore: StoreConfig, keyPassword: String)
case class KafkaConfig(aiven: KafkaClusterConfig)
case class KafkaClusterConfig(hosts: String,
                              topics: Map[String, String],
                              schemaRegistry: Option[SchemaRegistryConfig],
                              ssl: Option[SSLConfig],
                              groupId: String,
                              retry: Option[RetryConfig],
                              nativeProperties: Map[String, AnyRef])

object KafkaClusterConfig {

  implicit val consumerConfigReader: ConfigReader[KafkaClusterConfig] = new ConfigReader[KafkaClusterConfig] {
    override def from(cv: ConfigValue): Either[ConfigReaderFailures, KafkaClusterConfig] = {
      for {
        cfg   <- cv.to[Config]
        hosts <- cfg.getValue("hosts").to[String]
        topics <- if (cfg.hasPath("topics")) cfg.getValue("topics").to[Map[String, String]]
        else Right(Map.empty[String, String])
        schemaRegistry <- if (cfg.hasPath("schema-registry"))
          cfg.getValue("schema-registry").to[Option[SchemaRegistryConfig]]
        else Right(Option.empty[SchemaRegistryConfig])
        ssl     <- if (cfg.hasPath("ssl")) cfg.getValue("ssl").to[Option[SSLConfig]] else Right(Option.empty[SSLConfig])
        groupId <- cfg.getValue("group-id").to[String]
        retry <- if (cfg.hasPath("retry")) cfg.getValue("retry").to[Option[RetryConfig]]
        else Right(Option.empty[RetryConfig])
        nativePropertiesConfig <- if (cfg.hasPath("native-properties"))
          cfg.getValue("native-properties").to[Option[Config]]
        else Right(Option.empty[Config])
        nativeProperties = nativePropertiesConfig.map(parseKafkaClientsProperties).getOrElse(Map.empty)
      } yield KafkaClusterConfig(hosts, topics, schemaRegistry, ssl, groupId, retry, nativeProperties)
    }
  }

  private def parseKafkaClientsProperties(config: Config): Map[String, AnyRef] = {
    def collectKeys(c: ConfigObject, prefix: String, keys: Set[String]): Set[String] = {
      var result = keys
      val iter   = c.entrySet.iterator
      while (iter.hasNext) {
        val entry = iter.next()
        entry.getValue match {
          case o: ConfigObject =>
            result ++= collectKeys(o, prefix + entry.getKey + ".", Set.empty)
          case _: ConfigValue =>
            result += prefix + entry.getKey
          case _ =>
          // in case there would be something else
        }
      }
      result
    }

    val keys = collectKeys(config.root, "", Set.empty)
    keys.map(key => key -> config.getString(key)).toMap
  }

}

case class SchemaRegistryConfig(url: String, username: String, password: String, retry: RetryConfig)
