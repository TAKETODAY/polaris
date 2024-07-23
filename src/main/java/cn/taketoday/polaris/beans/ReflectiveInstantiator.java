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

package cn.taketoday.polaris.beans;

import java.lang.reflect.Constructor;

/**
 * based on java reflect
 *
 * @author TODAY 2020/9/20 21:55
 * @see Constructor#newInstance(Object...)
 */
final class ReflectiveInstantiator extends ConstructorAccessor {

  public ReflectiveInstantiator(Constructor<?> constructor) {
    super(constructor);
  }

  @Override
  public Object doInstantiate(final Object[] args) {
    return BeanUtils.newInstance(constructor, args);
  }

  @Override
  public String toString() {
    return "BeanInstantiator use reflective constructor: " + constructor;
  }

}
