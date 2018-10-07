package es.eriktorr.samples.resilient.orders.infrastructure.database;

import es.eriktorr.samples.resilient.orders.domain.model.Order;
import es.eriktorr.samples.resilient.orders.domain.model.OrderId;
import es.eriktorr.samples.resilient.orders.domain.model.OrderReference;
import es.eriktorr.samples.resilient.orders.domain.model.StoreId;
import io.vavr.Tuple2;
import lombok.val;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class OrdersRepository {

    private static final String INSERT_ORDER_SQL = "INSERT INTO orders (id, store, reference, description) VALUES (?, ?, ?, ?)";
    private static final String FIND_ORDER_BY_STORE_REFERENCE_SQL = "SELECT id, store, reference, description FROM orders " +
            "WHERE store = ? AND reference = ?";

    private final JdbcTemplate jdbcTemplate;

    public OrdersRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(Order order) {
        jdbcTemplate.update(INSERT_ORDER_SQL, order.getOrderId().getValue(), order.getStoreId().getValue(),
                order.getOrderReference().getValue(), order.getDescription());
    }

    public Order findBy(StoreId storeId, OrderReference orderReference) {
        try {
            return jdbcTemplate.queryForObject(FIND_ORDER_BY_STORE_REFERENCE_SQL,
                    new Object[]{ storeId.getValue(), orderReference.getValue() },
                    new int[]{ Types.VARCHAR, Types.VARCHAR },
                    (resultSet, rowNum) -> new Order(
                            new OrderId(resultSet.getString("id")),
                            new StoreId(resultSet.getString("store")),
                            new OrderReference(resultSet.getString("reference")),
                            resultSet.getString("description")
                    )
            );
        } catch (EmptyResultDataAccessException e) {
            return Order.INVALID;
        }
    }

    @Transactional
    public List<Tuple2<StoreId, OrderReference>> findDuplicate(List<Order> orders) {
        val tableName = "tmp" + RandomStringUtils.random(60);
        try {
            createTemporary(tableName);
            insertInto(orders, tableName);
            updateWithIds(tableName);
            return findDuplicateIn(tableName);
        } finally {
            drop(tableName);
        }
    }

    private void createTemporary(String tableName) {
        jdbcTemplate.execute(String.format("CREATE TEMPORARY TABLE %s(store VARCHAR(64) NOT NULL, " +
                "reference VARCHAR(64) NOT NULL, current_id VARCHAR(64))", tableName));
    }

    private void insertInto(List<Order> orders, String tableName) {
        List<Object[]> batch = orders.stream().map(
                order -> new Object[] {
                        order.getStoreId().getValue(),
                        order.getOrderReference().getValue()
                }
        ).collect(Collectors.toList());
        int[] insertCounts = jdbcTemplate.batchUpdate(String.format("INSERT INTO %s (store, reference) VALUES (?, ?)", tableName), batch);
        if (insertCounts.length != batch.size()) {
            throw new IllegalStateException("no all fields were inserted into the temporary table");
        }
    }

    private void updateWithIds(String tableName) {
        jdbcTemplate.execute(String.format("UPDATE %s SET current_id = id FROM orders WHERE %s.store = orders.store " +
                "AND %s.reference = orders.reference", tableName, tableName, tableName));
    }

    private List<Tuple2<StoreId, OrderReference>> findDuplicateIn(String tableName) {
        return jdbcTemplate.query(
                String.format("SELECT store, reference FROM %s WHERE current_id IS NOT NULL", tableName),
                new Object[]{},
                (resultSet, rowNum) -> new Tuple2<>(
                        new StoreId(resultSet.getString("store")),
                        new OrderReference(resultSet.getString("reference"))
                )
        );
    }

    private void drop(String tableName) {
        this.jdbcTemplate.execute(String.format("DROP TABLE %s", tableName));
    }

}