package dev.eventmesh.delivery;

import java.util.List;

public record DeliveryDetails(DeliverySummary delivery, List<DeliveryAttempt> attempts) {}
