/**
 * Copyright 2015-2017 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.autoconfigure.storage.elasticsearch.http;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import zipkin.storage.StorageComponent;
import zipkin.storage.elasticsearch.http.ElasticsearchHttpStorage;

@Configuration
@EnableConfigurationProperties(ZipkinElasticsearchHttpStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
@Conditional(ZipkinElasticsearchHttpStorageAutoConfiguration.HostsAreUrls.class)
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinElasticsearchHttpStorageAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  StorageComponent storage(ElasticsearchHttpStorage.Builder esHttpBuilder) {
    return esHttpBuilder.build();
  }

  @Bean
  ElasticsearchHttpStorage.Builder esHttpBuilder(
      ZipkinElasticsearchHttpStorageProperties elasticsearch,
      @Qualifier("zipkinElasticsearchHttp") OkHttpClient client,
      @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
      @Value("${zipkin.query.lookback:86400000}") int namesLookback) {
    return elasticsearch.toBuilder(client)
        .strictTraceId(strictTraceId)
        .namesLookback(namesLookback);
  }

  /** cheap check to see if we are likely to include urls */
  static final class HostsAreUrls implements Condition {
    @Override public boolean matches(ConditionContext condition, AnnotatedTypeMetadata md) {
      String hosts = condition.getEnvironment().getProperty("zipkin.storage.elasticsearch.hosts");
      return hosts != null && (hosts.contains("http://") || hosts.contains("https://"));
    }
  }
}
