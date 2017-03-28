/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.autoconfigure.ui;

import org.junit.After;
import org.junit.Test;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

public class ZipkinUiAutoConfigurationTest {

  AnnotationConfigApplicationContext context;

  @After
  public void close() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  public void indexHtmlFromClasspath() {
    context = createContext();

    assertThat(context.getBean(ZipkinUiAutoConfiguration.class).indexHtml)
        .isNotNull();
  }


  @Test
  public void canOverridesProperty_defaultLookback() {
    context = createContextWithOverridenProperty("zipkin.ui.defaultLookback:100");

    assertThat(context.getBean(ZipkinUiProperties.class).getDefaultLookback())
        .isEqualTo(100);
  }


  @Test
  public void canOverrideProperty_logsUrl() {
    final String url = "http://mycompany.com/kibana";
    context = createContextWithOverridenProperty("zipkin.ui.logs-url:"+ url);

    assertThat(context.getBean(ZipkinUiProperties.class).getLogsUrl()).isEqualTo(url);
  }

  @Test
  public void logsUrlIsNullIfOverridenByEmpty() {
    context = createContextWithOverridenProperty("zipkin.ui.logs-url:");

    assertThat(context.getBean(ZipkinUiProperties.class).getLogsUrl()).isNull();
  }

  @Test
  public void logsUrlIsNullByDefault() {
    context = createContext();

    assertThat(context.getBean(ZipkinUiProperties.class).getLogsUrl()).isNull();
  }

  private static AnnotationConfigApplicationContext createContext() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.register(PropertyPlaceholderAutoConfiguration.class, ZipkinUiAutoConfiguration.class);
    context.refresh();
    return context;
  }

  private static AnnotationConfigApplicationContext createContextWithOverridenProperty(String pair) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    addEnvironment(context, pair);
    context.register(PropertyPlaceholderAutoConfiguration.class, ZipkinUiAutoConfiguration.class);
    context.refresh();
    return context;
  }
}
