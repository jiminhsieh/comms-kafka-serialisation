package com.ovoenergy.comms.helpers

import com.ovoenergy.comms.model._
import com.ovoenergy.comms.model.email._
import com.ovoenergy.comms.model.print.{ComposedPrint, OrchestratedPrint}
import com.ovoenergy.comms.model.sms._

trait HasCommName[A] {
  def commName(a: A): String
}

object HasCommName {
  def instance[A](getCommName: A => String): HasCommName[A] = new HasCommName[A] {
    def commName(a: A): String = getCommName(a)
  }

  // TODO: For a rainy day- auto derive these using shapeless magic

  implicit val FailedCancellationHasCommName =
    HasCommName.instance[FailedCancellationV2](_.cancellationRequested.commName)
  implicit val FailedHasCommName            = HasCommName.instance[FailedV2](_.metadata.commManifest.name)
  implicit val OrchStartedHasCommName       = HasCommName.instance[OrchestrationStartedV2](_.metadata.commManifest.name)
  implicit val CancelledHasCommName         = HasCommName.instance[CancelledV2](_.cancellationRequested.commName)
  implicit val OrchestratedEmailHasCommName = HasCommName.instance[OrchestratedEmailV3](_.metadata.commManifest.name)
  implicit val OrchestratedSMSHasCommName   = HasCommName.instance[OrchestratedSMSV2](_.metadata.commManifest.name)
  implicit val OrchestratedPrintHasCommName = HasCommName.instance[OrchestratedPrint](_.metadata.commManifest.name)
  implicit val SMSProgressedHasCommName     = HasCommName.instance[SMSProgressedV2](_.metadata.commManifest.name)
  implicit val LinkClickedHasCommName       = HasCommName.instance[LinkClickedV2](_.metadata.commManifest.name)
  implicit val EmailProgressedHasCommName   = HasCommName.instance[EmailProgressedV2](_.metadata.commManifest.name)
  implicit val ComposedEmailHasCommName     = HasCommName.instance[ComposedEmailV3](_.metadata.commManifest.name)
  implicit val ComposedSMSHasCommName       = HasCommName.instance[ComposedSMSV3](_.metadata.commManifest.name)
  implicit val ComposedPrintHasCommName     = HasCommName.instance[ComposedPrint](_.metadata.commManifest.name)
  implicit val TriggeredHasCommName         = HasCommName.instance[TriggeredV3](_.metadata.commManifest.name)
}
