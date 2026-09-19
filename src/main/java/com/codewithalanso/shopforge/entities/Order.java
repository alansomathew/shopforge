package com.codewithalanso.shopforge.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "orders_user_id_idx", columnList = "user_id"),
    @Index(name = "orders_status_idx", columnList = "status"),
    @Index(name = "orders_created_at_desc_idx", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // order_number is filled in by the generate_order_number_trigger BEFORE INSERT (see
    // V1__init.sql) -- there's no Java code anywhere that sets it. insertable/updatable=false
    // tells Hibernate "never send this column yourself", but on its own that would leave this
    // field null in the Java object forever, even though the database row has a real value.
    // @Generated(event = EventType.INSERT) is the fix: it tells Hibernate to run one extra
    // SELECT immediately after every INSERT into this table, specifically to read this column's
    // trigger-assigned value back into the object -- so order.getOrderNumber() works right after
    // orderRepository.save(order), not just on a later re-fetch.
    @Generated(event = EventType.INSERT)
    @Column(name = "order_number", insertable = false, updatable = false, length = 30)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "guest_email")
    private String guestEmail;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "order_status")
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_address_id")
    private Address shippingAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address_snapshot", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> shippingAddressSnapshot;

    @Column(name = "shipping_method", length = 100)
    private String shippingMethod;

    @NotNull
    @Column(name = "shipping_cost", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @NotNull
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Postgres's `inet` type has no first-class JDBC equivalent, so Hibernate binds this plain
    // String field as VARCHAR by default -- which Postgres refuses to insert into an inet column
    // without an explicit cast (a real, pre-existing bug that blocked every checkout attempt).
    // @ColumnTransformer(write = "?::inet") tells Hibernate to wrap the bind parameter in that
    // cast at the SQL level. Same fix already used for RefreshToken.ipAddress elsewhere in this
    // codebase -- this field was just missing it.
    @Column(name = "ip_address", columnDefinition = "inet")
    @ColumnTransformer(write = "?::inet")
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Shipment shipment;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Payment payment;
}
