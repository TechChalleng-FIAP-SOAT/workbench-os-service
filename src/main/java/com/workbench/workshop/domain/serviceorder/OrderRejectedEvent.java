package com.workbench.workshop.domain.serviceorder;

import java.util.UUID;

public record OrderRejectedEvent(UUID serviceOrderId) {}
