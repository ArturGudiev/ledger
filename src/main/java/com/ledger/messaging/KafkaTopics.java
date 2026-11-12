package com.ledger.messaging;

public final class KafkaTopics {
  public static final String TRANSFERS = "ledger.transfers";
  public static final String PAYMENTS = "ledger.payments";
  public static final String WALLETS = "ledger.wallets";
  public static final String NOTIFICATIONS = "ledger.notifications";
  public static final String RETRY_SUFFIX = "retry";
  public static final String DLT_SUFFIX = "dlt";

  private KafkaTopics() {}

  public static String retryOf(String topic) {
    return topic + "." + RETRY_SUFFIX;
  }

  public static String dltOf(String topic) {
    return topic + "." + DLT_SUFFIX;
  }

  public static String topicFor(String aggregateType) {
    if (aggregateType == null) {
      return NOTIFICATIONS;
    }
    return switch (aggregateType.toUpperCase()) {
      case "TRANSFER", "TRANSFERS" -> TRANSFERS;
      case "PAYMENT", "PAYMENTS" -> PAYMENTS;
      case "WALLET", "WALLETS" -> WALLETS;
      default -> NOTIFICATIONS;
    };
  }
}
