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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import cn.taketoday.polaris.PropertyConditionStrategy.Condition;
import cn.taketoday.polaris.annotation.Order;
import cn.taketoday.polaris.annotation.OrderBy;
import cn.taketoday.polaris.logging.LogMessage;
import cn.taketoday.polaris.sql.MutableOrderByClause;
import cn.taketoday.polaris.sql.OrderByClause;
import cn.taketoday.polaris.sql.OrderBySource;
import cn.taketoday.polaris.sql.Restriction;
import cn.taketoday.polaris.sql.SimpleSelect;
import cn.taketoday.polaris.util.Nullable;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2024/2/19 19:56
 */
final class ExampleQuery extends SimpleSelectQueryStatement implements ConditionStatement, DebugDescriptive {

  private final Object example;

  private final EntityMetadata exampleMetadata;

  private final List<PropertyConditionStrategy> strategies;

  @Nullable
  private ArrayList<Condition> conditions;

  @Nullable
  private OrderByClause orderByClause;

  ExampleQuery(Object example, EntityMetadata exampleMetadata, List<PropertyConditionStrategy> strategies) {
    this.example = example;
    this.exampleMetadata = exampleMetadata;
    this.strategies = strategies;
  }

  ExampleQuery(EntityMetadataFactory factory, Object example, List<PropertyConditionStrategy> strategies) {
    this.example = example;
    this.strategies = strategies;
    this.exampleMetadata = factory.getEntityMetadata(example.getClass());
  }

  @Override
  protected void renderInternal(EntityMetadata metadata, SimpleSelect select) {
    scan(condition -> select.addRestriction(condition.restriction));
    select.orderBy(example instanceof OrderBySource source ? source.orderByClause() : orderByClause);
  }

  public void renderWhereClause(StringBuilder sql) {
    Restriction.render(scan(null), sql);
  }

  @Override
  public void renderWhereClause(EntityMetadata metadata, List<Restriction> restrictions) {
    restrictions.addAll(scan(null));
  }

  @Override
  public OrderByClause getOrderByClause(EntityMetadata metadata) {
    if (example instanceof OrderBySource source) {
      OrderByClause orderByClause = source.orderByClause();
      if (!orderByClause.isEmpty()) {
        return orderByClause;
      }
    }
    return orderByClause;
  }

  @Override
  public void setParameter(EntityMetadata metadata, PreparedStatement statement) throws SQLException {
    int idx = 1;
    for (var condition : scan(null)) {
      idx = condition.setParameter(statement, idx);
    }
  }

  @Override
  public String getDescription() {
    return "Query entities with example";
  }

  @Override
  public Object getDebugLogMessage() {
    return LogMessage.format("Query entity using example: {}", example);
  }

  private ArrayList<Condition> scan(@Nullable Consumer<Condition> consumer) {
    ArrayList<Condition> conditions = this.conditions;
    if (conditions == null) {
      conditions = new ArrayList<>(exampleMetadata.entityProperties.length);
      // apply class level order by
      applyOrderByClause();

      for (EntityProperty entityProperty : exampleMetadata.entityProperties) {
        Object propertyValue = entityProperty.getValue(example);
        if (propertyValue != null) {
          for (var strategy : strategies) {
            var condition = strategy.resolve(entityProperty, propertyValue);
            if (condition != null) {
              if (consumer != null) {
                consumer.accept(condition);
              }
              conditions.add(condition);
              break;
            }
          }
        }

        applyOrderByClause(entityProperty);
      }
      this.conditions = conditions;
    }
    else if (consumer != null) {
      conditions.forEach(consumer);
    }
    return conditions;
  }

  private void applyOrderByClause() {
    OrderBy orderBy = exampleMetadata.getAnnotation(OrderBy.class);
    if (orderBy != null) {
      String clause = orderBy.clause();
      if (!Constant.DEFAULT_NONE.equals(clause)) {
        orderByClause = OrderByClause.plain(clause);
      }
    }
  }

  private void applyOrderByClause(EntityProperty entityProperty) {
    if (!(orderByClause instanceof OrderByClause.Plain)) {
      OrderBy annotation = entityProperty.getAnnotation(OrderBy.class);
      if (annotation != null) {
        Order direction = annotation.direction();
        MutableOrderByClause mutable = (MutableOrderByClause) orderByClause;
        if (mutable == null) {
          mutable = OrderByClause.mutable();
          this.orderByClause = mutable;
        }
        mutable.orderBy(entityProperty.columnName, direction);
      }
    }
  }
}
