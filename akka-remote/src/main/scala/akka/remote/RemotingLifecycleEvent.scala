/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.remote

import akka.event.{ LoggingAdapter, Logging }
import akka.actor.{ ActorSystem, Address }
import akka.event.Logging.LogLevel

@SerialVersionUID(1L)
sealed trait RemotingLifecycleEvent extends Serializable {
  def logLevel: Logging.LogLevel
}

@SerialVersionUID(1L)
sealed trait AssociationEvent extends RemotingLifecycleEvent {
  def localAddress: Address
  def remoteAddress: Address
  def inbound: Boolean
  protected def eventName: String
  final def getRemoteAddress: Address = remoteAddress
  final def getLocalAddress: Address = localAddress
  final def isInbound: Boolean = inbound
  override def toString: String = s"$eventName [$localAddress]${if (inbound) " <- " else " -> "}[$remoteAddress]"
}

@SerialVersionUID(1L)
final case class AssociatedEvent(
  localAddress: Address,
  remoteAddress: Address,
  inbound: Boolean)
  extends AssociationEvent {

  protected override def eventName: String = "Associated"
  override def logLevel: Logging.LogLevel = Logging.DebugLevel

}

@SerialVersionUID(1L)
final case class DisassociatedEvent(
  localAddress: Address,
  remoteAddress: Address,
  inbound: Boolean)
  extends AssociationEvent {
  protected override def eventName: String = "Disassociated"
  override def logLevel: Logging.LogLevel = Logging.DebugLevel
}

@SerialVersionUID(1L)
final case class AssociationErrorEvent(
  cause: Throwable,
  localAddress: Address,
  remoteAddress: Address,
  inbound: Boolean) extends AssociationEvent {
  override def logLevel: Logging.LogLevel = Logging.ErrorLevel
  protected override def eventName: String = "AssociationError"
  override def toString: String = s"${super.toString}: Error [${cause.getMessage}] [${Logging.stackTraceFor(cause)}]"
  def getCause: Throwable = cause
}

@SerialVersionUID(1L)
final case class RemotingListenEvent(listenAddresses: Set[Address]) extends RemotingLifecycleEvent {
  def getListenAddresses: java.util.Set[Address] =
    scala.collection.JavaConverters.setAsJavaSetConverter(listenAddresses).asJava
  override def logLevel: Logging.LogLevel = Logging.InfoLevel
  override def toString: String = "Remoting now listens on addresses: " + listenAddresses.mkString("[", ", ", "]")
}

@SerialVersionUID(1L)
case object RemotingShutdownEvent extends RemotingLifecycleEvent {
  override def logLevel: Logging.LogLevel = Logging.InfoLevel
  override val toString: String = "Remoting shut down"
}

@SerialVersionUID(1L)
final case class RemotingErrorEvent(cause: Throwable) extends RemotingLifecycleEvent {
  def getCause: Throwable = cause
  override def logLevel: Logging.LogLevel = Logging.ErrorLevel
  override def toString: String = s"Remoting error: [${cause.getMessage}] [${Logging.stackTraceFor(cause)}]"
}

/**
 * INTERNAL API
 */
private[remote] class EventPublisher(system: ActorSystem, log: LoggingAdapter, logLevel: LogLevel) {
  // NOTE: overrideLogLevel is a workaround because no additional field can be added to
  // case class AssociationErrorEvent. This is properly fixed in 2.3
  def notifyListeners(message: RemotingLifecycleEvent, overrideLogLevel: Option[LogLevel]): Unit = {
    system.eventStream.publish(message)
    if (logLevel <= overrideLogLevel.getOrElse(message.logLevel)) log.log(message.logLevel, "{}", message)
  }
}