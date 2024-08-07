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

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Objects;

import cn.taketoday.polaris.annotation.GeneratedId;
import cn.taketoday.polaris.annotation.Id;
import cn.taketoday.polaris.beans.BeanProperty;
import cn.taketoday.polaris.util.Assert;

/**
 * Determine ID property
 *
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2022/8/16 21:48
 */
public interface IdPropertyDiscover {

  String DEFAULT_ID_PROPERTY = "id";

  /**
   * determine that {@code property} is an ID property
   *
   * @param property candidate
   * @return is an ID property or not
   */
  boolean isIdProperty(BeanProperty property);

  // static

  /**
   * 将两个策略合并到一起，尝试了第一个策略之后如果不是 ID 则会尝试第二个策略
   * 也就是 {@code next} 变量
   *
   * @param next next resolver
   * @return returns a new resolving chain
   */
  default IdPropertyDiscover and(IdPropertyDiscover next) {
    return property -> isIdProperty(property) || next.isIdProperty(property);
  }

  /**
   * composite pattern
   */
  static IdPropertyDiscover composite(IdPropertyDiscover... discovers) {
    Assert.notNull(discovers, "IdPropertyDiscover is required");
    return composite(List.of(discovers));
  }

  /**
   * composite pattern
   */
  static IdPropertyDiscover composite(List<IdPropertyDiscover> discovers) {
    Assert.notNull(discovers, "IdPropertyDiscover is required");
    return beanProperty -> {

      for (IdPropertyDiscover discover : discovers) {
        if (discover.isIdProperty(beanProperty)) {
          return true;
        }
      }

      return false;
    };
  }

  /**
   * @param name property name
   */
  static IdPropertyDiscover forPropertyName(String name) {
    Assert.notNull(name, "property-name is required");
    return property -> Objects.equals(name, property.getName());
  }

  /**
   * use {@link Id} or {@link GeneratedId}
   */
  static IdPropertyDiscover forIdAnnotation() {
    return forAnnotation(Id.class).and(forAnnotation(GeneratedId.class));
  }

  /**
   * Use input {@code annotationType} to determine id column
   *
   * @param annotationType Annotation type
   * @return Annotation based {@link IdPropertyDiscover}
   */
  static IdPropertyDiscover forAnnotation(Class<? extends Annotation> annotationType) {
    Assert.notNull(annotationType, "annotationType is required");
    return property -> property.isAnnotationPresent(annotationType);
  }

}
