package com.workbench.workshop.adapter.in.web.serviceorder;

import com.workbench.workshop.domain.serviceorder.Decision;

/**
 * Decisão do cliente sobre o orçamento da OS.
 *
 * @param decision APPROVED ou REJECTED
 * @param reason   motivo (usado na recusa; opcional)
 */
public record DecideOrderRequest(Decision decision, String reason) {}
