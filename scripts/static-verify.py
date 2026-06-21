#!/usr/bin/env python3
"""Repository-level structural verification that needs no external services."""
from __future__ import annotations
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
errors: list[str] = []
modules = [line.split("'", 2)[1] for line in (ROOT / "settings.gradle").read_text().splitlines() if line.startswith("include '")]
for module in modules:
    if not (ROOT / module / "build.gradle").exists():
        errors.append(f"Missing build.gradle: {module}")

for path in ROOT.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    if "TODO" in text or "FIXME" in text:
        errors.append(f"Unresolved marker: {path.relative_to(ROOT)}")
    declaration = re.search(r"\n(?:public\s+)?(?:final\s+)?(?:class|record|enum|interface)\s+\w+", text)
    if declaration and "/**" not in text[: declaration.start()]:
        errors.append(f"Missing top-level Javadoc: {path.relative_to(ROOT)}")

for path in ROOT.rglob("*.json"):
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        errors.append(f"Invalid JSON {path.relative_to(ROOT)}: {exc}")

required_topics = {
    "marketplace.inventory.commands.v1", "marketplace.inventory.events.v1",
    "marketplace.payment.commands.v1", "marketplace.payment.events.v1",
    "marketplace.shipping.commands.v1", "marketplace.shipping.events.v1",
    "marketplace.audit.events.v1",
}
topic_script = (ROOT / "docker/kafka/create-topics.sh").read_text()
for topic in required_topics:
    if topic not in topic_script:
        errors.append(f"Topic is not provisioned: {topic}")

for module in ("inventory-service", "order-service", "payment-service", "shipping-service"):
    candidates = list((ROOT / module / "src/main/resources/db/migration").glob("V1__*.sql"))
    if not candidates:
        errors.append(f"No V1 migration: {module}")
        continue
    sql = candidates[0].read_text()
    for table in ("outbox_event", "inbox_event"):
        if table not in sql:
            errors.append(f"{module} lacks {table}")


# Guard the selected 2026 baseline and critical bootstrapping details.
compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
for required in (
    "apache/kafka:4.3.0",
    "KAFKA_LOG_DIRS: /var/lib/kafka/data",
    "KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR: 3",
    "KAFKA_SHARE_COORDINATOR_STATE_TOPIC_MIN_ISR: 2",
    "JWT_ISSUER: http://localhost:8081",
    "JWT_AUDIENCE: marketplace-api",
):
    if required not in compose:
        errors.append(f"Docker Compose lacks required Kafka setting: {required}")


resource_services = (
    "api-gateway", "catalog-service", "cart-service", "inventory-service",
    "order-service", "payment-service", "shipping-service", "audit-service",
)
for module in resource_services:
    configs = list((ROOT / module / "src/main/resources").glob("application.y*ml"))
    combined = "\n".join(config.read_text(encoding="utf-8") for config in configs)
    if "issuer-uri" not in combined or "audiences" not in combined:
        errors.append(f"JWT issuer/audience validation is incomplete: {module}")

all_java = "\n".join(path.read_text(encoding="utf-8") for path in ROOT.rglob("*.java"))
if "com.fasterxml.jackson" in all_java:
    errors.append("Jackson 2 package remains; Spring Boot 4 baseline must use Jackson 3")
if 'scanBasePackages = "com.github.dimitryivaniuta.marketplace"' in all_java:
    errors.append("Broad application package scanning can cross-load other service modules")

auto_imports = ROOT / "common-platform/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
if not auto_imports.exists():
    errors.append("Common platform auto-configuration imports are missing")

for module in ("inventory-service", "order-service", "payment-service", "shipping-service"):
    config_files = list((ROOT / module / "src/main/resources").glob("application.y*ml"))
    outbox_enabled = False
    for config_file in config_files:
        config_text = config_file.read_text(encoding="utf-8")
        outbox_enabled = outbox_enabled or bool(re.search(
            r"(?ms)^marketplace:\s*$.*?^  outbox:\s*$\n    enabled:\s*true\s*$",
            config_text,
        ))
    if not outbox_enabled:
        errors.append(f"Outbox publisher is not explicitly enabled: {module}")


# Guard p95/p99 observability and hot-product protections.
latency_rules = ROOT / "observability/prometheus/rules/marketplace-latency.yml"
if not latency_rules.exists():
    errors.append("Prometheus p95/p99 recording rules are missing")
else:
    rules_text = latency_rules.read_text(encoding="utf-8")
    for required in (
        "histogram_quantile(",
        "marketplace:http_server_request_duration_seconds:p95",
        "marketplace:http_server_request_duration_seconds:p99",
        "marketplace:inventory_reservation_duration_seconds:p99",
        "marketplace:catalog_cache_load_duration_seconds:p99",
        "marketplace:kafka_listener_duration_seconds:p99",
        "marketplace:order_saga_duration_seconds:p99",
    ):
        if required not in rules_text:
            errors.append(f"Latency recording rules lack: {required}")

for required_path in (
    "performance/hot-product-read.js",
    "performance/catalog-cache-penetration.js",
    "observability/grafana/dashboards/marketplace-latency.json",
    "docs/adr/0004-tail-latency-and-hot-products.md",
    "inventory-service/src/main/resources/db/migration/V3__hot_sku_inventory_buckets.sql",
):
    if not (ROOT / required_path).exists():
        errors.append(f"Hot-product asset is missing: {required_path}")

kafka_reliability = (ROOT / "common-platform/src/main/java/com/github/dimitryivaniuta/marketplace/common/messaging/KafkaReliabilityConfiguration.java").read_text(encoding="utf-8")
for required in ("setBackOffFunction", "TransientContentionException", "20L", "1_000L"):
    if required not in kafka_reliability:
        errors.append(f"Kafka contention retry policy lacks: {required}")

inventory_repository = (ROOT / "inventory-service/src/main/java/com/github/dimitryivaniuta/marketplace/inventory/repository/InventoryRepository.java").read_text(encoding="utf-8")
for required in ("FOR UPDATE OF b SKIP LOCKED", "LIMIT :quantity", "inventory_bucket"):
    if required not in inventory_repository:
        errors.append(f"Striped inventory implementation lacks: {required}")

order_config = (ROOT / "order-service/src/main/resources/application.yml").read_text(encoding="utf-8")
for required in ("marketplace.order.saga.duration: 10m", "marketplace.order.saga.duration: 1s,5s,10s,30s,60s,120s,300s,600s"):
    if required not in order_config:
        errors.append(f"Order Saga histogram bounds lack: {required}")

catalog_config = (ROOT / "catalog-service/src/main/resources/application.yml").read_text(encoding="utf-8")
for required in ("CATALOG_NEGATIVE_CACHE_TTL", "CATALOG_CACHE_LOAD_TIMEOUT", "CATALOG_REDIS_TIMEOUT", "CATALOG_DB_ACQUIRE_TIMEOUT"):
    if required not in catalog_config:
        errors.append(f"Catalog latency bound is missing: {required}")

catalog_cache = (ROOT / "catalog-service/src/main/java/com/github/dimitryivaniuta/marketplace/catalog/cache/HotProductCache.java").read_text(encoding="utf-8")
for required in ("setIfAbsent", "jitteredTtl", "RELEASE_LOCK", "localCache", "coalesce", "MISSING_SENTINEL"):
    if required not in catalog_cache:
        errors.append(f"Catalog stampede protection lacks: {required}")

gateway_config = (ROOT / "api-gateway/src/main/resources/application.yml").read_text(encoding="utf-8")
for required in ("CATALOG_ROUTE_RATE", "catalogRouteKeyResolver", "CATALOG_HOT_KEY_RATE"):
    source = gateway_config if required != "catalogRouteKeyResolver" else (ROOT / "api-gateway/src/main/java/com/github/dimitryivaniuta/marketplace/gateway/configuration/GatewayConfiguration.java").read_text(encoding="utf-8")
    if required not in source:
        errors.append(f"Gateway hot-product protection lacks: {required}")

for required in (
    "CATALOG_CACHE_LOAD_TIMEOUT: ${CATALOG_CACHE_LOAD_TIMEOUT",
    "CATALOG_ROUTE_RATE: ${CATALOG_ROUTE_RATE",
    "GATEWAY_REDIS_TIMEOUT: ${GATEWAY_REDIS_TIMEOUT",
):
    if required not in compose:
        errors.append(f"Docker Compose does not propagate tuning setting: {required}")

for required in ("allkeys-lfu", "noeviction", "redis-cache:", "prom/prometheus:v3.12.0", "grafana/grafana-oss:13.0.2"):
    if required not in compose:
        errors.append(f"Docker Compose lacks hot-product observability setting: {required}")

if errors:
    print("Static verification FAILED")
    print("\n".join(f"- {error}" for error in errors))
    sys.exit(1)
print(f"Static verification passed: {len(modules)} modules, {sum(1 for _ in ROOT.rglob('*.java'))} Java files")
