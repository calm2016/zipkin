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

import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.MergeById;
import zipkin.internal.Util;
import zipkin.storage.DependenciesTest;
import zipkin.storage.InMemorySpanStore;
import zipkin.storage.InMemoryStorage;

import static zipkin.TestObjects.DAY;
import static zipkin.TestObjects.TODAY;
import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;
import static zipkin.internal.Util.midnightUTC;

public abstract class ElasticsearchDependenciesTest extends DependenciesTest {

  protected abstract ElasticsearchStorage storage();

  @Override public void clear() throws IOException {
    storage().clear();
  }

  /**
   * The current implementation does not include dependency aggregation. It includes retrieval of
   * pre-aggregated links.
   *
   * <p>This uses {@link InMemorySpanStore} to prepare links and {@link #writeDependencyLinks(List,
   * long)}} to store them.
   */
  @Override public void processDependencies(List<Span> spans) {
    InMemoryStorage mem = new InMemoryStorage();
    mem.spanConsumer().accept(spans);
    List<DependencyLink> links = mem.spanStore().getDependencies(TODAY + DAY, null);

    // This gets or derives a timestamp from the spans
    long midnight = midnightUTC(guessTimestamp(MergeById.apply(spans).get(0)) / 1000);
    writeDependencyLinks(links, midnight);
  }

  protected void writeDependencyLinks(List<DependencyLink> links, long timestampMillis) {
    long midnight = Util.midnightUTC(timestampMillis);
    TransportClient client = ((NativeClient) storage().client()).client;
    BulkRequestBuilder request = client.prepareBulk();
    for (DependencyLink link : links) {
      request.add(client.prepareIndex(
          storage().indexNameFormatter.indexNameForTimestamp(midnight),
          ElasticsearchConstants.DEPENDENCY_LINK)
          .setId(link.parent + "|" + link.child) // Unique constraint
          .setSource(
              "parent", link.parent,
              "child", link.child,
              "callCount", link.callCount));
    }
    request.execute().actionGet();
    client.admin().indices().flush(new FlushRequest()).actionGet();
  }
}
