package com.ovoenergy.comms.helpers

import com.ovoenergy.comms.serialisation.Retry.RetryConfig

case class KafkaRootConfig(kafka: KafkaConfig)
case class StoreConfig(location: String, password: String)
case class SSLConfig(keystore: StoreConfig, truststore: StoreConfig, keyPassword: String)
case class KafkaConfig(aiven: KafkaClusterConfig)
case class KafkaClusterConfig(hosts: String,
                              topics: Map[String, String],
                              schemaRegistry: Option[SchemaRegistryConfig],
                              ssl: Option[SSLConfig],
                              groupId: String,
                              retry: Option[RetryConfig])

case class SchemaRegistryConfig(url: String, username: String, password: String, retry: RetryConfig)
