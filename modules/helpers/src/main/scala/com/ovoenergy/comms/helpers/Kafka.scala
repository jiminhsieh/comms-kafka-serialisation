package com.ovoenergy.comms.helpers

import com.ovoenergy.comms.model._
import com.ovoenergy.comms.model.email._
import com.ovoenergy.comms.model.sms._
import com.typesafe.config.Config
import shapeless.HNil

case class CommsKafkaCluster(clusterName: String)(implicit config: Config) {
  implicit val kafkaConfig: KafkaClusterConfig = {
    val confOrError = pureconfig.loadConfig[KafkaClusterConfig](config.getConfig(s"kafka.$clusterName"))
    confOrError match {
      case Left(err) => throw new Exception(s"Failed to read config with errors: $err")
      case Right(c)  => c
    }
  }

  val retry: Option[RetryConfig] = kafkaConfig.retry
}

object Kafka {
  def aiven(implicit config: Config) = new CommsKafkaCluster("aiven") {
    val triggered = new {
      val v3 = Topic[TriggeredV3]("triggeredV3", useMagicByte = true)
    }
    val composedEmail = new {
      val v2 = Topic[ComposedEmailV2]("composedEmailV2", useMagicByte = false)
    }
    val composedSms = new {
      val v2 = Topic[ComposedSMSV2]("composedSmsV2", useMagicByte = false)
    }
    val failed = new {
      val v2 = Topic[FailedV2]("failedV2", useMagicByte = false)
    }
    val issuedForDelivery = new {
      val v2 = Topic[IssuedForDeliveryV2]("issuedForDeliveryV2", useMagicByte = false)
    }
    val orchestrationStarted = new {
      val v2 = Topic[OrchestrationStartedV2]("orchestrationStartedV2", useMagicByte = false)
    }
    val orchestratedEmail = new {
      val v3 = Topic[OrchestratedEmailV3]("orchestratedEmailV3", useMagicByte = false)
    }
    val orchestratedSMS = new {
      val v2 = Topic[OrchestratedSMSV2]("orchestratedSmsV2", useMagicByte = false)
    }
    val progressedEmail = new {
      val v2 = Topic[EmailProgressedV2]("progressedEmailV2", useMagicByte = false)
    }
    val progressedSMS = new {
      val v2 = Topic[SMSProgressedV2]("progressedSmsV2", useMagicByte = false)
    }
    val linkClicked = new {
      val v2 = Topic[LinkClickedV2]("linkClickedV2", useMagicByte = false)
    }
    val cancellationRequested = new {
      val v2 = Topic[CancellationRequestedV2]("cancellationRequestedV2", useMagicByte = true)
    }
    val failedCancellation = new {
      val v2 = Topic[FailedCancellationV2]("failedCancellationV2", useMagicByte = false)
    }
    val cancelled = new {
      val v2 = Topic[CancelledV2]("cancelledV2", useMagicByte = false)
    }

    val allTopics = triggered.v3 :: composedEmail.v2 :: composedSms.v2 :: failed.v2 :: issuedForDelivery.v2 ::
      orchestratedEmail.v3 :: orchestratedSMS.v2 :: progressedEmail.v2 :: progressedSMS.v2 :: linkClicked.v2 ::
      cancellationRequested.v2 :: failedCancellation.v2 :: cancelled.v2 :: HNil
  }

  def legacy(implicit config: Config) = new CommsKafkaCluster("legacy") {
    val triggered = new {
      val v2 = Topic[TriggeredV2]("triggeredV2")
      val v3 = Topic[TriggeredV3]("triggeredV3")
    }
    val cancellationRequested = new {
      val v1 = Topic[CancellationRequested]("cancellationRequested")
      val v2 = Topic[CancellationRequestedV2]("cancellationRequestedV2")
    }

    val allTopics = triggered.v2 :: triggered.v3 :: cancellationRequested.v1 :: cancellationRequested.v2 :: HNil
  }
}
