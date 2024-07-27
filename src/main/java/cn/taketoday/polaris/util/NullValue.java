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

package cn.taketoday.polaris.util;

import java.io.Serial;
import java.io.Serializable;

/**
 * Simple serializable class that serves as a {@code null} replacement
 * for cache stores which otherwise do not support {@code null} values.
 *
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2021/9/25 10:42
 */
public final class NullValue implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  public static final NullValue INSTANCE = new NullValue();

  private NullValue() { }

  @Serial
  private Object readResolve() {
    return INSTANCE;
  }

  @Override
  public int hashCode() {
    return NullValue.class.hashCode();
  }

  @Override
  public String toString() {
    return "NullValue";
  }

}
