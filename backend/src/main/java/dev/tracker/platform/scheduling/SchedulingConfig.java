package dev.tracker.platform.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Housekeeping jobs (session sweeps and similar). Durable background work goes through the outbox. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {}
