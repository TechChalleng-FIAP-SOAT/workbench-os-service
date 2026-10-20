package com.workbench.workshop.adapter.in.web.serviceorder;

import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import lombok.Data;

@Data
public class UpdateOrderRequest {
    private OrderServiceStatusEnum status;
}
