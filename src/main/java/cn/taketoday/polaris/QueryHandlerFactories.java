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

import cn.taketoday.polaris.query.MappedStatementHandlerFactory;
import cn.taketoday.polaris.util.Nullable;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2024/4/10 17:55
 */
final class QueryHandlerFactories implements QueryHandlerFactory {

  private final List<QueryHandlerFactory> factories;

  QueryHandlerFactories(EntityMetadataFactory metadataFactory) {
    var serviceLoader = ServiceLoader.load(QueryHandlerFactory.class);
    var list = new ArrayList<>(serviceLoader.stream().map(ServiceLoader.Provider::get).toList());
    list.add(new MapQueryHandlerFactory());
    list.add(new MappedStatementHandlerFactory());
    list.add(new DefaultQueryHandlerFactory(metadataFactory));
    this.factories = List.copyOf(list);
  }

  @Nullable
  @Override
  public QueryStatement createQuery(Object param) {
    for (QueryHandlerFactory factory : factories) {
      QueryStatement query = factory.createQuery(param);
      if (query != null) {
        return query;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public ConditionStatement createCondition(Object param) {
    for (QueryHandlerFactory factory : factories) {
      ConditionStatement condition = factory.createCondition(param);
      if (condition != null) {
        return condition;
      }
    }
    return null;
  }

}
