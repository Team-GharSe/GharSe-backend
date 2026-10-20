package com.gharse.review;

import com.gharse.catalog.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.math.BigDecimal;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Reviews of a product, newest first. Pass a PageRequest to cap the size. */
    @Query("""
            select r
            from Review r, OrderItem i
            where r.orderItemId = i.id
              and i.productId = :productId
            order by r.createdAt desc, r.id desc
            """)
    List<Review> findForProductNewestFirst(@Param("productId") Long productId, Pageable pageable);

    boolean existsByOrderItemId(Long orderItemId);

    /** Average rating over every review of this product (null if none). */
    @Query("""
            select avg(r.rating)
            from Review r, OrderItem i
            where r.orderItemId = i.id
              and i.productId = :productId
            """)
    Double averageRatingForProduct(@Param("productId") Long productId);

    /** Number of reviews of this product. */
    @Query("""
            select count(r)
            from Review r, OrderItem i
            where r.orderItemId = i.id
              and i.productId = :productId
            """)
    long countForProduct(@Param("productId") Long productId);

    /**
     * Writes the cached rating on the product. This is a direct UPDATE, not
     * "load Product and save it", so it does not bump Product's @Version and
     * cannot collide with a stock change made during checkout.
     */
    @Modifying
    @Query("""
            update Product p
            set p.averageRating = :avg, p.reviewCount = :count
            where p.id = :productId
            """)
    int updateProductRating(@Param("productId") Long productId,
                            @Param("avg") BigDecimal avg,
                            @Param("count") int count);
}