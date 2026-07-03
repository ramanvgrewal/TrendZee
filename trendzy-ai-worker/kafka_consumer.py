import json
import logging
import os
import signal
import sys
from typing import Any

from confluent_kafka import Consumer, KafkaError, KafkaException

from env_loader import load_project_env
from intelligence_updater import process_and_upsert

ENV_PATH = load_project_env()

LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
logging.basicConfig(
    level=LOG_LEVEL,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)

LOGGER = logging.getLogger(__name__)

TOPIC = os.getenv("KAFKA_TOPIC", "raw-signals-topic")


def _build_consumer() -> Consumer:
    broker = os.getenv("KAFKA_BROKER", "localhost:9092")
    group_id = os.getenv("KAFKA_GROUP_ID", "ai-trend-cluster-group")

    consumer_config = {
        "bootstrap.servers": broker,
        "group.id": group_id,
        "auto.offset.reset": os.getenv("KAFKA_AUTO_OFFSET_RESET", "earliest"),
        "enable.auto.commit": False,
        "session.timeout.ms": 45000,    # Tell Kafka to wait 45 seconds before assuming worker is dead
        "max.poll.interval.ms": 300000  # Allow up to 5 minutes to process a heavy AI message
    }

    LOGGER.info("Creating Kafka consumer for broker=%s group_id=%s", broker, group_id)
    return Consumer(consumer_config)


def _extract_signal_id(payload: str) -> str:
    raw_value = payload.strip()
    if not raw_value:
        raise ValueError("Received empty Kafka message.")

    if raw_value.startswith("{"):
        message_data: dict[str, Any] = json.loads(raw_value)
        signal_id = (
            message_data.get("signalId")
            or message_data.get("signal_id")
            or message_data.get("_id")
        )
        if not signal_id:
            raise ValueError(f"signalId missing from message payload: {message_data}")
        return str(signal_id)

    return raw_value


def main() -> int:
    consumer = _build_consumer()
    running = True

    if ENV_PATH:
        LOGGER.info("Loaded environment from %s", ENV_PATH)
    else:
        LOGGER.warning("No .env file found. Using only process environment variables.")

    def _handle_shutdown(signum: int, _frame: Any) -> None:
        nonlocal running
        LOGGER.info("Received shutdown signal %s", signum)
        running = False

    signal.signal(signal.SIGINT, _handle_shutdown)
    signal.signal(signal.SIGTERM, _handle_shutdown)

    try:
        consumer.subscribe([TOPIC])
        LOGGER.info("Subscribed to Kafka topic=%s", TOPIC)

        while running:
            message = consumer.poll(1.0)
            if message is None:
                continue

            if message.error():
                if message.error().code() == KafkaError._PARTITION_EOF:
                    LOGGER.debug(
                        "Reached end of partition topic=%s partition=%s offset=%s",
                        message.topic(),
                        message.partition(),
                        message.offset(),
                    )
                    continue
                raise KafkaException(message.error())

            payload = message.value().decode("utf-8")
            LOGGER.info(
                "Received Kafka message topic=%s partition=%s offset=%s payload=%s",
                message.topic(),
                message.partition(),
                message.offset(),
                payload,
            )

            try:
                signal_id = _extract_signal_id(payload)
                process_and_upsert(signal_id)
                consumer.commit(message=message, asynchronous=False)
                LOGGER.info("Committed Kafka offset for signal_id=%s", signal_id)
            except Exception:
                LOGGER.exception("Failed to process Kafka message payload=%s", payload)

    except KeyboardInterrupt:
        LOGGER.info("Keyboard interrupt received, shutting down consumer")
    except Exception:
        LOGGER.exception("Kafka consumer terminated due to an unrecoverable error")
        return 1
    finally:
        LOGGER.info("Closing Kafka consumer")
        consumer.close()

    return 0


if __name__ == "__main__":
    sys.exit(main())
