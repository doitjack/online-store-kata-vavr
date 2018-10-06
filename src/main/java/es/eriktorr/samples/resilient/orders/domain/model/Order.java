package es.eriktorr.samples.resilient.orders.domain.model;

import lombok.Value;

@Value
public class Order {

    private final OrderId orderId;
    private final StoreId storeId;
    private final OrderReference orderReference;
    private final String description;

    public static Order from(OrderId orderId, Order other) {
        return new Order(
                orderId,
                other.storeId,
                other.orderReference,
                other.description
        );
    }

}