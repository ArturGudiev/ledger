package com.ledger.gateway;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;

public final class RestClientFactory {

  private RestClientFactory() {}

  public static JdkClientHttpRequestFactory withTimeouts(Duration connectTimeout, Duration readTimeout) {
    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(readTimeout);
    return requestFactory;
  }
}