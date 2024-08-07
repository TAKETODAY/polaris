/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.taketoday.polaris;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import cn.taketoday.polaris.support.DefaultConditionStrategy;
import cn.taketoday.polaris.support.FuzzyQueryConditionStrategy;
import cn.taketoday.polaris.support.WhereAnnotationConditionStrategy;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2024/4/10 16:53
 */
final class DefaultQueryHandlerFactory implements QueryHandlerFactory {

  static final List<PropertyConditionStrategy> strategies;

  private final EntityMetadataFactory factory;

  static {
    var serviceLoader = ServiceLoader.load(PropertyConditionStrategy.class);
    var list = new ArrayList<>(serviceLoader.stream().map(ServiceLoader.Provider::get).toList());
    list.add(new WhereAnnotationConditionStrategy());
    list.add(new FuzzyQueryConditionStrategy());
    list.add(new DefaultConditionStrategy());
    strategies = List.copyOf(list);
  }

  public DefaultQueryHandlerFactory(EntityMetadataFactory factory) {
    this.factory = factory;
  }

  @Override
  public QueryStatement createQuery(Object param) {
    return new ExampleQuery(factory, param, strategies);
  }

  @Override
  public ConditionStatement createCondition(Object param) {
    return new ExampleQuery(factory, param, strategies);
  }

}
