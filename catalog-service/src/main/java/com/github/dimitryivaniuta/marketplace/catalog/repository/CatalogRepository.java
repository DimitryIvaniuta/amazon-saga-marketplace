package com.github.dimitryivaniuta.marketplace.catalog.repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.github.dimitryivaniuta.marketplace.catalog.api.CatalogContracts;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** SQL-optimized catalog persistence gateway. */
@Repository
@RequiredArgsConstructor
public class CatalogRepository {

    /** Reactive SQL client. */
    private final DatabaseClient databaseClient;
    /** JSON mapper. */
    private final JsonMapper objectMapper;

    /** @return all active products with variants in one SQL query */
    public Flux<CatalogContracts.Product> findActiveProducts() {
        return productQuery("WHERE p.active = true", null);
    }

    /** @param id product identifier @return product when found */
    public Mono<CatalogContracts.Product> findProduct(UUID id) {
        return productQuery("WHERE p.id = :id", id).singleOrEmpty();
    }

    /** @param skuId SKU identifier @return active variant */
    public Mono<CatalogContracts.Variant> findVariant(UUID skuId) {
        return databaseClient.sql("""
                SELECT id, sku_code, attributes::text AS attributes_json, price_minor, currency, active
                  FROM sku
                 WHERE id = :id AND active = true
                """)
                .bind("id", skuId)
                .map((row, metadata) -> new CatalogContracts.Variant(
                        row.get("id", UUID.class), row.get("sku_code", String.class),
                        readMap(row.get("attributes_json", String.class)),
                        row.get("price_minor", Long.class), row.get("currency", String.class),
                        Boolean.TRUE.equals(row.get("active", Boolean.class))))
                .one();
    }

    /** @param id product id @param request product data @return completion */
    public Mono<Void> insertProduct(UUID id, CatalogContracts.CreateProduct request) {
        return databaseClient.sql("""
                INSERT INTO product(id, name, description, category, active, created_at, updated_at)
                VALUES (:id, :name, :description, :category, true, now(), now())
                """)
                .bind("id", id).bind("name", request.name()).bind("description", request.description())
                .bind("category", request.category()).fetch().rowsUpdated().then();
    }

    /** @param productId product id @param id variant id @param request variant data @return completion */
    public Mono<Void> insertVariant(UUID productId, UUID id, CatalogContracts.CreateVariant request) {
        String attributes;
        try {
            attributes = objectMapper.writeValueAsString(request.attributes());
        } catch (Exception exception) {
            return Mono.error(exception);
        }
        return databaseClient.sql("""
                INSERT INTO sku(id, product_id, sku_code, attributes, price_minor, currency, active, created_at, updated_at)
                VALUES (:id, :productId, :skuCode, CAST(:attributes AS jsonb), :priceMinor, :currency, true, now(), now())
                """)
                .bind("id", id).bind("productId", productId).bind("skuCode", request.skuCode())
                .bind("attributes", attributes).bind("priceMinor", request.priceMinor())
                .bind("currency", request.currency().toUpperCase()).fetch().rowsUpdated().then();
    }

    private Flux<CatalogContracts.Product> productQuery(String where, UUID id) {
        String sql = """
                SELECT p.id, p.name, p.description, p.category, p.active, p.created_at,
                       COALESCE(jsonb_agg(jsonb_build_object(
                           'id', s.id, 'skuCode', s.sku_code, 'attributes', s.attributes,
                           'priceMinor', s.price_minor, 'currency', s.currency, 'active', s.active)
                           ORDER BY s.sku_code) FILTER (WHERE s.id IS NOT NULL), '[]'::jsonb)::text AS variants_json
                  FROM product p
                  LEFT JOIN sku s ON s.product_id = p.id AND s.active = true
                """ + where + " GROUP BY p.id ORDER BY p.created_at DESC";
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        if (id != null) {
            spec = spec.bind("id", id);
        }
        return spec.map((row, metadata) -> new CatalogContracts.Product(
                row.get("id", UUID.class), row.get("name", String.class), row.get("description", String.class),
                row.get("category", String.class), Boolean.TRUE.equals(row.get("active", Boolean.class)),
                readVariants(row.get("variants_json", String.class)),
                row.get("created_at", OffsetDateTime.class).toInstant())).all();
    }

    private List<CatalogContracts.Variant> readVariants(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot deserialize catalog variants", exception);
        }
    }

    private Map<String, String> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot deserialize SKU attributes", exception);
        }
    }
}
