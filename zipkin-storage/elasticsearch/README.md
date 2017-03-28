# storage-elasticsearch

This Elasticsearch 2 storage component includes a `GuavaSpanStore` and `GuavaSpanConsumer`.
Until [zipkin-dependencies](https://github.com/openzipkin/zipkin-dependencies) is run, `ElasticsearchSpanStore.getDependencies()` will return empty.

The implementation uses Elasticsearch Java API's [transport client](https://www.elastic.co/guide/en/elasticsearch/guide/master/_talking_to_elasticsearch.html#_java_api) for optimal performance.

`zipkin.storage.elasticsearch.ElasticsearchStorage.Builder` includes defaults
that will operate against a local Elasticsearch installation.

## Indexes
Spans are stored into daily indices, for example spans with a timestamp
falling on 2016/03/19 will be stored in the index named 'zipkin-2016-03-19'.
There is no support for TTL through this SpanStore. It is recommended
instead to use [Elastic Curator](https://www.elastic.co/guide/en/elasticsearch/client/curator/current/about.html)
to remove indices older than the point you are interested in.

### Customizing daily index format
The daily index format can be adjusted in two ways. You can change the
index prefix from 'zipkin' to something else. You can also change
the date separator from '-' to something else.
`ElasticsearchStorage.Builder.index` and `ElasticsearchStorage.Builder.dateSeparator`
control the daily index format.

For example, spans with a timestamp falling on 2016/03/19 end up in the
index 'zipkin-2016-03-19'. When the date separator is '.', the index
would be 'zipkin-2016.03.19'.

### String Mapping
The Zipkin api implies aggregation and exact match (keyword) on string
fields named `traceId` and `name`, as well nested fields named
`serviceName`, `key` and `value`. Indexing on these fields is limited to
256 characters eventhough storage is currently unbounded.

### Timestamps
Zipkin's timestamps are in epoch microseconds, which is not a supported date type in Elasticsearch.
In consideration of tools like like Kibana, this component adds "timestamp_millis" when writing
spans. This is mapped to the Elasticsearch date type, so can be used to any date-based queries.

### Trace Identifiers
Unless `ElasticsearchStorage.Builder.strictTraceId` is set to false,
trace identifiers are unanalyzed keywords (exact string match). This
means that trace IDs should be written fixed length as either 16 or 32
lowercase hex characters, corresponding to 64 or 128 bit length. If
writing a custom collector in a different language, make sure you trace
identifiers the same way.

#### Migrating from 64 to 128-bit trace IDs
When [migrating from 64 to 128-bit trace IDs](../../zipkin-server/README.md#migrating-from-64-to-128-bit-trace-ids),
`ElasticsearchStorage.Builder.strictTraceId` will be false, and traceId
fields will be tokenized to support mixed lookup. This setting should
only be used temporarily, but is explained below.

The index template tokenizes trace identifiers to match on either 64-bit
or 128-bit length. This allows span lookup by 64-bit trace ID to include
spans reported with 128-bit variants of the same id. This allows interop
with tools who only support 64-bit ids, and buys time for applications
to upgrade to 128-bit instrumentation.

For example, application A starts a trace with a 128-bit `traceId`
"48485a3953bb61246b221d5bc9e6496c". The next hop, application B, doesn't
yet support 128-bit ids, B truncates `traceId` to "6b221d5bc9e6496c".
When `SpanStore.getTrace(toLong("6b221d5bc9e6496c"))` executes, it
is able to retrieve spans with the longer `traceId`, due to tokenization
setup in the index template.

To see this in action, you can run a test command like so against one of
your indexes:

```bash
# the output below shows which tokens will match on the trace id supplied.
$ curl -s localhost:9200/test_zipkin_http-2016-10-26/_analyze -d '{
      "text": "48485a3953bb61246b221d5bc9e6496c",
      "analyzer": "traceId_analyzer"
  }'|jq '.tokens|.[]|.token'
  "48485a3953bb61246b221d5bc9e6496c"
  "6b221d5bc9e6496c"
```

### Span and service Names
Zipkin defines span and service names as lowercase. At write time, any
mixed case span or service names are downcased. If writing a custom
collector in a different language, make sure you write span and service
names in lowercase. Also, if there are any custom query tools, ensure
inputs are downcased.

## Testing this component
This module conditionally runs integration tests against a local Elasticsearch instance.

Tests are configured to automatically access Elasticsearch started with its defaults.
To ensure tests execute, download an Elasticsearch 2.x distribution, extract it, and run `bin/elasticsearch`. 

If you run tests via Maven or otherwise when Elasticsearch is not running,
you'll notice tests are silently skipped.
```
Results :

Tests run: 50, Failures: 0, Errors: 0, Skipped: 48
```

This behaviour is intentional: We don't want to burden developers with
installing and running all storage options to test unrelated change.
That said, all integration tests run on pull request via Travis.
