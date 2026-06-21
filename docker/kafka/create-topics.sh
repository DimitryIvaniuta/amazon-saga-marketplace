#!/usr/bin/env bash
set -euo pipefail
BOOTSTRAP="kafka-1:29092"
TOPICS=(
  marketplace.inventory.commands.v1
  marketplace.inventory.events.v1
  marketplace.payment.commands.v1
  marketplace.payment.events.v1
  marketplace.shipping.commands.v1
  marketplace.shipping.events.v1
  marketplace.audit.events.v1
  marketplace.inventory.commands.v1.DLT
  marketplace.inventory.events.v1.DLT
  marketplace.payment.commands.v1.DLT
  marketplace.payment.events.v1.DLT
  marketplace.shipping.commands.v1.DLT
  marketplace.shipping.events.v1.DLT
  marketplace.audit.events.v1.DLT
)
for topic in "${TOPICS[@]}"; do
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$topic" --partitions 12 --replication-factor 3 \
    --config min.insync.replicas=2
done
