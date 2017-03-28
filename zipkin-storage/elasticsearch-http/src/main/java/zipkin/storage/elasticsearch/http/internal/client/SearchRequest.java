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
package zipkin.storage.elasticsearch.http.internal.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class SearchRequest {
  /**
   * The maximum results returned in a query. This only affects non-aggregation requests.
   *
   * <p>Not configurable as it implies adjustments to the index template (index.max_result_window)
   *
   * <p> See https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-from-size.html
   */
  static final int MAX_RESULT_WINDOW = 10000; // the default elasticsearch allowed limit

  transient final List<String> indices;
  transient final String type;

  Integer size = MAX_RESULT_WINDOW;
  Boolean _source;
  Object query;
  Map<String, Aggregation> aggs;

  SearchRequest(List<String> indices, String type) {
    this.indices = indices;
    this.type = type;
  }

  public static class Filters extends LinkedList<Object> {
    public Filters addRange(String field, long from, Long to) {
      add(new Range(field, from, to));
      return this;
    }

    public Filters addTerm(String field, String value) {
      add(new Term(field, value));
      return this;
    }

    public Filters addNestedTerms(Collection<String> nestedFields, String value) {
      add(_nestedTermsEqual(nestedFields, value));
      return this;
    }

    public Filters addNestedTerms(Map<String, String> nestedTerms) {
      List<SearchRequest.Term> terms = new ArrayList<>();
      String field = null;
      for (Map.Entry<String, String> nestedTerm : nestedTerms.entrySet()) {
        terms.add(new Term(field = nestedTerm.getKey(), nestedTerm.getValue()));
      }
      add(new NestedBoolQuery(field.substring(0, field.indexOf('.')), "must", terms));
      return this;
    }
  }

  public SearchRequest filters(Filters filters) {
    return query(new BoolQuery("must", filters));
  }

  static SearchRequest.BoolQuery _nestedTermsEqual(Collection<String> nestedFields, String value) {
    List<SearchRequest.NestedBoolQuery> conditions = new ArrayList<>();
    for (String nestedField : nestedFields) {
      conditions.add(new NestedBoolQuery(nestedField.substring(0, nestedField.indexOf('.')), "must",
          new SearchRequest.Term(nestedField, value)));
    }
    return new SearchRequest.BoolQuery("should", conditions);
  }

  public static SearchRequest forIndicesAndType(List<String> indices, String type) {
    return new SearchRequest(indices, type);
  }

  public SearchRequest term(String field, String value) {
    return query(new Term(field, value));
  }

  public SearchRequest terms(String field, List<String> values) {
    return query(new Terms(field, values));
  }

  public SearchRequest addAggregation(Aggregation agg) {
    size = null; // we return aggs, not source data
    _source = false;
    if (aggs == null) aggs = new LinkedHashMap<>();
    aggs.put(agg.field, agg);
    return this;
  }

  String tag() {
    return aggs != null ? "aggregation" : "search";
  }

  SearchRequest query(Object filter) {
    query = Collections.singletonMap("bool", Collections.singletonMap("filter", filter));
    return this;
  }

  static class Term {
    final Map<String, String> term;

    Term(String field, String value) {
      term = Collections.singletonMap(field, value);
    }
  }

  static class Terms {
    final Map<String, List<String>> terms;

    Terms(String field, List<String> values) {
      this.terms = Collections.singletonMap(field, values);
    }
  }

  static class Range {
    final Map<String, Bounds> range;

    Range(String field, long from, Long to) {
      range = Collections.singletonMap(field, new Bounds(from, to));
    }

    static class Bounds {
      final long from;
      final Long to;
      final boolean include_lower = true;
      final boolean include_upper = true;

      Bounds(long from, Long to) {
        this.from = from;
        this.to = to;
      }
    }
  }

  static class NestedBoolQuery {
    final Map<String, Object> nested;

    NestedBoolQuery(String path, String condition, List<Term> terms) {
      nested = new LinkedHashMap<>(2);
      nested.put("path", path);
      nested.put("query", new BoolQuery(condition, terms));
    }

    NestedBoolQuery(String path, String condition, Term term) {
      nested = new LinkedHashMap<>(2);
      nested.put("path", path);
      nested.put("query", new BoolQuery(condition, term));
    }
  }

  static class BoolQuery {
    final Map<String, Object> bool;

    BoolQuery(String op, Object clause) {
      bool = Collections.singletonMap(op, clause);
    }
  }
}
