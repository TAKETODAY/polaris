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
import java.util.List;

import cn.taketoday.polaris.annotation.OrderBy;
import cn.taketoday.polaris.sql.OrderByClause;
import cn.taketoday.polaris.sql.Restriction;
import cn.taketoday.polaris.util.Descriptive;
import cn.taketoday.polaris.util.Nullable;

/**
 * Condition statement
 *
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @see Pageable
 * @see Descriptive
 * @see DebugDescriptive
 * @since 1.0 2024/3/31 15:51
 */
public interface ConditionStatement {

  /**
   * Render where clause
   *
   * @param metadata query entity metadata
   * @param restrictions restrictions list
   */
  void renderWhereClause(EntityMetadata metadata, List<Restriction> restrictions);

  /**
   * order by
   */
  @Nullable
  default OrderByClause getOrderByClause(EntityMetadata metadata) {
    OrderBy orderBy = metadata.getAnnotation(OrderBy.class);
    if (orderBy != null) {
      String clause = orderBy.value();
      if (!Constant.DEFAULT_NONE.equals(clause)) {
        return OrderByClause.plain(clause);
      }
    }
    return null;
  }

  /**
   * apply statement parameters
   *
   * @param metadata entity info
   * @param statement JDBC statement
   */
  void setParameter(EntityMetadata metadata, PreparedStatement statement) throws SQLException;

}
