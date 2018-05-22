package com.ovoenergy.comms.helpers

import com.ovoenergy.comms.model._
import com.ovoenergy.comms.model.email._
import com.ovoenergy.comms.model.sms._
import com.ovoenergy.comms.model.print._
import com.ovoenergy.comms.serialisation.Retry.RetryConfig
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
      val v3 = Topic[TriggeredV3]("triggeredV3")
      val v4 = Topic[TriggeredV4]("triggeredV4")
    }
    val composedEmail = new {
      val v2 = Topic[ComposedEmailV2]("composedEmailV2")
      val v3 = Topic[ComposedEmailV3]("composedEmailV3")
      val v4 = Topic[ComposedEmailV4]("composedEmailV4")
    }
    val composedSms = new {
      val v2 = Topic[ComposedSMSV2]("composedSmsV2")
      val v3 = Topic[ComposedSMSV3]("composedSmsV3")
      val v4 = Topic[ComposedSMSV4]("composedSmsV4")
    }
    val composedPrint = new {
      val v1 = Topic[ComposedPrint]("composedPrint")
      val v2 = Topic[ComposedPrint]("composedPrintV2")
    }
    val failed = new {
      val v2 = Topic[FailedV2]("failedV2")
      val v3 = Topic[FailedV3]("failedV3")
    }
    val issuedForDelivery = new {
      val v2 = Topic[IssuedForDeliveryV2]("issuedForDeliveryV2")
      val v3 = Topic[IssuedForDeliveryV3]("issuedForDeliveryV3")
    }
    val orchestrationStarted = new {
      val v2 = Topic[OrchestrationStartedV2]("orchestrationStartedV2")
      val v3 = Topic[OrchestrationStartedV3]("orchestrationStartedV3")
    }
    val orchestratedEmail = new {
      val v3 = Topic[OrchestratedEmailV3]("orchestratedEmailV3")
      val v4 = Topic[OrchestratedEmailV4]("orchestratedEmailV4")
    }
    val orchestratedSMS = new {
      val v2 = Topic[OrchestratedSMSV2]("orchestratedSmsV2")
      val v3 = Topic[OrchestratedSMSV3]("orchestratedSmsV3")
    }
    val orchestratedPrint = new {
      val v1 = Topic[OrchestratedPrint]("orchestratedPrint")
      val v2 = Topic[OrchestratedPrintV2]("orchestratedPrintV2")
    }
    val progressedEmail = new {
      val v2 = Topic[EmailProgressedV2]("progressedEmailV2")
      val v3 = Topic[EmailProgressedV3]("progressedEmailV3")
    }
    val progressedSMS = new {
      val v2 = Topic[SMSProgressedV2]("progressedSmsV2")
      val v3 = Topic[SMSProgressedV3]("progressedSmsV3")
    }
    val linkClicked = new {
      val v2 = Topic[LinkClickedV2]("linkClickedV2")
      val v3 = Topic[LinkClickedV3]("linkClickedV3")
    }
    val cancellationRequested = new {
      val v2 = Topic[CancellationRequestedV2]("cancellationRequestedV2")
      val v3 = Topic[CancellationRequestedV3]("cancellationRequestedV3")
    }
    val failedCancellation = new {
      val v2 = Topic[FailedCancellationV2]("failedCancellationV2")
      val v3 = Topic[FailedCancellationV3]("failedCancellationV3")
    }
    val cancelled = new {
      val v2 = Topic[CancelledV2]("cancelledV2")
      val v3 = Topic[CancelledV3]("cancelledV3")
    }

    val allTopics = triggered.v3 :: triggered.v4 :: composedEmail.v2 :: composedEmail.v3 :: composedEmail.v4 ::
      composedSms.v2 :: composedSms.v3 :: composedSms.v4 :: composedPrint.v1 :: composedPrint.v2 :: failed.v2 ::
      failed.v3 :: issuedForDelivery.v2 :: issuedForDelivery.v3 :: orchestratedEmail.v3 :: orchestratedEmail.v4 ::
      orchestratedSMS.v2 :: orchestratedSMS.v3 :: progressedEmail.v2 :: progressedEmail.v3 :: progressedSMS.v2 ::
      progressedSMS.v3 :: linkClicked.v2 :: linkClicked.v3 :: cancellationRequested.v2 :: cancellationRequested.v3 ::
      failedCancellation.v2 :: failedCancellation.v3 :: cancelled.v2 :: cancelled.v3 :: HNil
  }

}
