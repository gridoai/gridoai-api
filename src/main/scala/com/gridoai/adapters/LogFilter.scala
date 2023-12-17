package com.gridoai;

import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import ch.qos.logback.classic.spi.ILoggingEvent

class HealthCheckFilter extends Filter[ILoggingEvent]:
  override def decide(event: ILoggingEvent): FilterReply =
    if (
      event.getLoggerName.contains("Http4sDefaultServerLog") &&
      event.getMessage.contains("GET /health")
    ) FilterReply.DENY
    else
      FilterReply.NEUTRAL
