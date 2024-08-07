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

package cn.taketoday.polaris.support;

import cn.taketoday.polaris.Constant;
import cn.taketoday.polaris.EntityProperty;
import cn.taketoday.polaris.PropertyConditionStrategy;
import cn.taketoday.polaris.annotation.Trim;
import cn.taketoday.polaris.annotation.Where;
import cn.taketoday.polaris.sql.Restriction;
import cn.taketoday.polaris.util.Nullable;

/**
 * 处理 {@link Where} 注解
 *
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2024/2/25 00:02
 */
public class WhereAnnotationConditionStrategy implements PropertyConditionStrategy {

  @Nullable
  @Override
  public Condition resolve(EntityProperty entityProperty, Object propertyValue) {
    if (propertyValue instanceof String string && entityProperty.isPresent(Trim.class)) {
      propertyValue = string.trim();
    }

    // render where clause
    Where annotation = entityProperty.getAnnotation(Where.class);
    if (annotation != null) {
      String value = annotation.value();
      if (!Constant.DEFAULT_NONE.equals(value)) {
        return new Condition(propertyValue, Restriction.plain(value), entityProperty);
      }
      else {
        String condition = annotation.condition();
        if (Constant.DEFAULT_NONE.equals(condition)) {
          return new Condition(propertyValue, Restriction.equal(entityProperty.columnName), entityProperty);
        }
      }
    }
    return null;
  }

}
