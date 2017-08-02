package com.ovoenergy.comms.helpers

import java.time.Duration
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

case class KafkaRootConfig(kafka: KafkaConfig)
case class StoreConfig(location: String, password: String)
case class SSLConfig(keystore: StoreConfig, truststore: StoreConfig, keyPassword: String)
case class KafkaConfig(aiven: KafkaClusterConfig)
case class RetryConfig(attempts: Int, initialInterval: FiniteDuration, exponent: Double) {
  val backoff: (Int) => FiniteDuration = {
    Retry.Backoff.exponential(initialInterval, exponent)
  }
}
case class KafkaClusterConfig(hosts: String,
                              topics: Map[String, String],
                              schemaRegistry: Option[SchemaRegistryConfig],
                              ssl: Option[SSLConfig],
                              groupId: String,
                              retry: Option[RetryConfig])
case class SchemaRegistryConfig(url: String, username: String, password: String)
