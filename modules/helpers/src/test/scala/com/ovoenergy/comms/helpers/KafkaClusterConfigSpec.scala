package com.ovoenergy.comms.helpers

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}

class KafkaClusterConfigSpec extends WordSpec with Matchers {

  "KafkaClusterConfig" should {
    "parse native properties" in {

      val config = ConfigFactory.parseString("""
          |kafka.aiven {
          |  hosts: "kafka-uat.ovo-uat.aivencloud.com:13581"
          |
          |  schema-registry {
          |
          |    url: "https://kafka-uat.ovo-uat.aivencloud.com:13584"
          |    username: "comms-platform-service-user"
          |    password: "k8wrtln2bwmpud4s"
          |
          |    retry {
          |      attempts: 3
          |      initial-interval: 500ms
          |      exponent: 1.5
          |    }
          |  }
          |
          |  ssl {
          |    keystore: {
          |      location: "keystore.jks"
          |      password: "keystore-secret"
          |    }
          |    truststore: {
          |      location: "client.truststore.jks"
          |      password: "truststore-secret"
          |    }
          |    key-password: "key-secret"
          |  }
          |
          |  topics {
          |    test = "test.3.4.5"
          |  }
          |
          |  group-id: "comms-test-1"
          |
          |  native-properties {
          |    auto.offset.reset = earliest
          |  }
          |}
        """.stripMargin)

      val kafkaCluster = CommsKafkaCluster("aiven")(config)
      kafkaCluster.kafkaConfig.topics should (contain key "test" and equal(Map("test" -> "test.3.4.5")))
      kafkaCluster.kafkaConfig.nativeProperties should (contain key "auto.offset.reset" and equal(
        Map("auto.offset.reset" -> "earliest")))

    }
  }

}
