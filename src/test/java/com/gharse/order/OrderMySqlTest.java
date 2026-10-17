package com.gharse.order;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.common.exception.ConflictException;
import com.gharse.seller.SellerProfileRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real MySQL schema/repository/transaction tests. Test rows roll back. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = OrderMySqlTest.Config.class)
@EnabledIfEnvironmentVariable(named = "GHARSE_ORDERS_TEST_DB_URL", matches = ".+")
@Transactional
class OrderMySqlTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {OrderRepository.class, SellerProfileRepository.class})
    static class Config {
        @Bean DataSource dataSource() throws Exception {
            String url = System.getenv("GHARSE_ORDERS_TEST_DB_URL");
            String user = System.getenv("GHARSE_ORDERS_TEST_DB_USER");
            String password = System.getenv("GHARSE_ORDERS_TEST_DB_PASSWORD");
            if (user == null || password == null) throw new IllegalStateException("Supply test DB credentials.");
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, user, password);
            try (Connection connection = dataSource.getConnection()) {
                String database = connection.getCatalog();
                if (database == null || !database.endsWith("_orders_test")) {
                    throw new IllegalStateException("Use a disposable database ending in _orders_test.");
                }
            }
            // Run the existing V1 from the project; never supply a duplicate migration.
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
            return dataSource;
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.gharse.order", "com.gharse.seller");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "validate");
            factory.setJpaProperties(properties);
            return factory;
        }

        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean OrderService orderService(OrderRepository orders, OrderItemRepository items,
                                       SellerProfileRepository sellers) {
            return new OrderService(orders, items, sellers);
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService service;
    @Autowired OrderRepository orders;
    @Autowired EntityManagerFactory factory;
    private Long orderId;
    private Long productId;
    private Long paymentId;
    private Long buyerId;
    private Long sellerUserId;
    private Long sellerProfileId;

    @BeforeEach
    void fixture() {
        buyerId = insert("INSERT INTO users(name, phone, password_hash) VALUES (?, ?, ?)",
            "Order test buyer", token().substring(0, 12), "test-only-nonlogin-hash");
        sellerUserId = insert("INSERT INTO users(name, phone, password_hash) VALUES (?, ?, ?)",
            "Order test seller", token().substring(0, 12), "test-only-nonlogin-hash");
        sellerProfileId = insert("INSERT INTO seller_profile(user_id, business_name, latitude, longitude) VALUES (?, ?, ?, ?)",
            sellerUserId, "Order test store", 19.0, 72.0);
        Long categoryId = insert("INSERT INTO category(name) VALUES (?)", "Order test " + token());
        productId = insert("INSERT INTO product(seller_profile_id, category_id, name, price, fulfilment_type) VALUES (?, ?, ?, ?, ?)",
            sellerProfileId, categoryId, "Original product", 75.50, "READY_STOCK");
        paymentId = insert("INSERT INTO payment(buyer_id, razorpay_order_id, amount_total, status) VALUES (?, ?, ?, ?)",
            buyerId, "test-" + token(), 151, "SUCCESS");
        Order order = orders.saveAndFlush(new Order(paymentId, buyerId, sellerProfileId,
            FulfilmentMode.SELF_DELIVERY, "Historical address"));
        orderId = order.getId();
        insert("INSERT INTO order_item(order_id, product_id, product_name_snapshot, price_snapshot, quantity) VALUES (?, ?, ?, ?, ?)",
            orderId, productId, "Original product", 75.50, 2);
    }

    @Test
    void buyerAndSellerQueriesUseRealRepositories() {
        var buyer = new AuthenticatedUser(buyerId, "BUYER");
        var seller = new AuthenticatedUser(sellerUserId, "SELLER");
        assertEquals(orderId, service.myOrders(buyer).get(0).orderId());
        assertEquals(orderId, service.getOrder(seller, orderId).orderId());
        assertEquals(1, service.sellerOrders(seller, OrderStatus.CONFIRMED).size());
        assertTrue(service.sellerOrders(seller, OrderStatus.READY).isEmpty());
    }

    @Test
    void statusUpdateUsesRealLockQueryAndPersists() {
        var seller = new AuthenticatedUser(sellerUserId, "SELLER");
        assertEquals(OrderStatus.IN_PRODUCTION,
            service.updateStatus(seller, orderId, OrderStatus.IN_PRODUCTION).status());
        assertEquals("IN_PRODUCTION", jdbc.queryForObject(
            "SELECT status FROM orders WHERE order_id = ?", String.class, orderId));
    }

    @Test
    void illegalTransitionDoesNotChangeDatabaseStatus() {
        assertThrows(ConflictException.class, () -> service.updateStatus(
            new AuthenticatedUser(sellerUserId, "SELLER"), orderId, OrderStatus.READY));
        assertEquals("CONFIRMED", jdbc.queryForObject(
            "SELECT status FROM orders WHERE order_id = ?", String.class, orderId));
    }

    @Test
    void snapshotsSurviveProductEdits() {
        jdbc.update("UPDATE product SET name = ?, price = ? WHERE product_id = ?", "Changed title", 999, productId);
        var item = service.getOrder(new AuthenticatedUser(buyerId, "BUYER"), orderId).items().get(0);
        assertEquals("Original product", item.productNameSnapshot());
        assertEquals(0, item.priceSnapshot().compareTo(new java.math.BigDecimal("75.50")));
        assertEquals("Historical address", service.getOrder(new AuthenticatedUser(buyerId, "BUYER"), orderId)
            .deliveryAddressSnapshot());
    }

    @Test
    void databaseRejectsSecondOrderForSamePayment() {
        assertThrows(DataIntegrityViolationException.class,
            () -> orders.saveAndFlush(new Order(paymentId, buyerId, sellerProfileId, FulfilmentMode.PICKUP, null)));
    }

    private static String token() { return UUID.randomUUID().toString().replace("-", ""); }

    private Long insert(String sql, Object... values) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Generated key missing.");
        return key.longValue();
    }
}
