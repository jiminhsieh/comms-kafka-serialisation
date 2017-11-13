package com.ovoenergy.comms.helpers

import com.ovoenergy.comms.model.{CancellationRequested, LoggableEvent}
import org.slf4j.{LoggerFactory, MDC}

trait TraceTokenProvider[E] {
  def traceToken(e: E): String
}

trait EventLogger[E] {
  def debug(e: E, message: String): Unit
  def info(e: E, message: String): Unit
  def warn(e: E, message: String): Unit
  def warn(e: E, message: String, error: Throwable): Unit
  def error(e: E, message: String): Unit
  def error(e: E, message: String, error: Throwable): Unit
}

trait EventLoggerL1 {
  implicit def summonLoggerFromLoggableEvent[E <: LoggableEvent]: EventLogger[E] = {
    new EventLogger[E] {
      def loggerName: String = getClass.getSimpleName.reverse.dropWhile(_ == '$').reverse

      lazy val log = LoggerFactory.getLogger(loggerName)

      override def debug(e: E, message: String): Unit =
        log(e.mdcMap, () => {
          log.debug(message + s", event: ${e.loggableString}")
        })

      override def info(e: E, message: String): Unit =
        log(e.mdcMap, () => {
          log.info(message + s", event: ${e.loggableString}")
        })

      override def warn(e: E, message: String): Unit = log(e.mdcMap, () => log.warn(message))

      override def warn(e: E, message: String, error: Throwable): Unit = {
        log(e.mdcMap, () => log.warn(message, error))
      }

      override def error(e: E, message: String): Unit = log(e.mdcMap, () => log.error(message))

      override def error(e: E, message: String, error: Throwable): Unit = {
        log(e.mdcMap, () => log.error(message, error))
      }

      private def log(mdcMap: Map[String, String], loggingFunction: () => Unit) {
        try {
          mdcMap.foreach { case (k, v) => MDC.put(k, v) }
          loggingFunction()
        } finally {
          mdcMap.foreach { case (k, _) => MDC.remove(k) }
        }
      }
    }
  }

  implicit def summonLoggerFromTraceTokenProvider[E: TraceTokenProvider]: EventLogger[E] = {
    val tokenProvider = implicitly[TraceTokenProvider[E]]
    new EventLogger[E] {
      def loggerName: String = getClass.getSimpleName.reverse.dropWhile(_ == '$').reverse

      lazy val log = LoggerFactory.getLogger(loggerName)

      override def debug(e: E, message: String): Unit = {
        log(Map("traceToken" -> tokenProvider.traceToken(e)), () => log.debug(message))
      }

      override def info(e: E, message: String): Unit = {
        log(Map("traceToken" -> tokenProvider.traceToken(e)), () => log.info(message))
      }

      override def warn(e: E, message: String): Unit = {
        log(Map("traceToken" -> tokenProvider.traceToken(e)), () => log.warn(message))
      }

      override def warn(e: E, message: String, error: Throwable): Unit = {
        log(Map("traceToken" -> tokenProvider.traceToken(e)), () => log.warn(message, error))
      }

      override def error(e: E, message: String): Unit = {
        log(Map("traceToken" -> tokenProvider.traceToken(e)), () => log.error(message))
      }

      override def error(e: E, message: String, error: Throwable): Unit = {
        log(Map("traceToken" -> tokenProvider.traceToken(e)), () => log.error(message, error))
      }

      private def log(mdcMap: Map[String, String], loggingFunction: () => Unit) {
        try {
          mdcMap.foreach { case (k, v) => MDC.put(k, v) }
          loggingFunction()
        } finally {
          mdcMap.foreach { case (k, _) => MDC.remove(k) }
        }
      }
    }
  }
}

object EventLogger extends EventLoggerL1 {
  private def traceTokenExtractor[T](f: T => String) = new TraceTokenProvider[T] {
    override def traceToken(e: T): String = f(e)
  }

  implicit val cancellationRequested: TraceTokenProvider[CancellationRequested] =
    traceTokenExtractor[CancellationRequested](_.metadata.traceToken)
}
