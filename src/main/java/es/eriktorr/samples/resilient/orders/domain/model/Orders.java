package es.eriktorr.samples.resilient.orders.domain.model;

import io.vavr.collection.Set;
import lombok.Value;

@Value
public class Orders {

    private final Set<Order> orders;

}
