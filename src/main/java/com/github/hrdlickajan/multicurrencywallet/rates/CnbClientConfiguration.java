package com.github.hrdlickajan.multicurrencywallet.rates;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class CnbClientConfiguration {

    @Bean(destroyMethod = "close")
    HttpClient cnbHttpClient(@Value("${wallet.cnb.connect-timeout}") Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    RestClient cnbClient(RestClient.Builder builder, HttpClient cnbHttpClient,
                        @Value("${wallet.cnb.url}") String url,
                        @Value("${wallet.cnb.request-timeout}") Duration requestTimeout) {
        var factory = new JdkClientHttpRequestFactory(cnbHttpClient);
        factory.setReadTimeout(requestTimeout);
        return builder.baseUrl(url).requestFactory(factory).build();
    }
}