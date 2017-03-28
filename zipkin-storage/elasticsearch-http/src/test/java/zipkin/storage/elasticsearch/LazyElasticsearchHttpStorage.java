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
package zipkin.storage.elasticsearch;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import okhttp3.OkHttpClient;
import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.HttpWaitStrategy;
import zipkin.Component;
import zipkin.internal.LazyCloseable;
import zipkin.storage.elasticsearch.http.HttpClientBuilder;

@Deprecated
class LazyElasticsearchHttpStorage extends LazyCloseable<ElasticsearchStorage> implements TestRule {

  final String image;

  GenericContainer container;

  LazyElasticsearchHttpStorage(String image) {
    this.image = image;
  }

  @Override protected ElasticsearchStorage compute() {
    try {
      container = new GenericContainer(image)
          .withExposedPorts(9200)
          .waitingFor(new HttpWaitStrategy().forPath("/"));
      container.start();
      System.out.println("Will use TestContainers Elasticsearch instance");
    } catch (Exception e) {
      // Ignore
    }

    ElasticsearchStorage result = computeStorageBuilder().build();
    Component.CheckResult check = result.check();
    if (check.ok) {
      return result;
    } else {
      throw new AssumptionViolatedException(check.exception.getMessage(), check.exception);
    }
  }

  public ElasticsearchStorage.Builder computeStorageBuilder() {
    ElasticsearchStorage.Builder builder =
        ElasticsearchStorage.builder(HttpClientBuilder.create(new OkHttpClient()))
            .index("test_zipkin_http").flushOnWrites(true);

    if (container != null && container.isRunning()) {
      String endpoint = String.format("http://%s:%d", container.getContainerIpAddress(),
          container.getMappedPort(9200));
      builder.hosts(ImmutableList.of(endpoint));
    } else {
      // Use localhost if we failed to start a container (i.e. Docker is not available)
      builder.hosts(ImmutableList.of("localhost:9200"));
    }

    return builder;
  }

  @Override public void close() {
    try {
      ElasticsearchStorage storage = maybeNull();

      if (storage != null) storage.close();
    } catch (IOException e) {
      Throwables.propagate(e);
    } finally {
      if (container != null) container.stop();
    }
  }

  @Override public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        get();
        try {
          base.evaluate();
        } finally {
          close();
        }
      }
    };
  }
}
