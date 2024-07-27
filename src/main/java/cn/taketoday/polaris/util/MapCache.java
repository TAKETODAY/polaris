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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Map cache
 *
 * @param <Key> key type
 * @param <Param> param type, extra computing param type
 * @param <Value> value type
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0
 */
public class MapCache<Key, Value, Param> {

  private final Map<Key, Value> mapping;

  /** default mapping function */
  @Nullable
  private final Function<Key, Value> mappingFunction;

  public MapCache() {
    this(new HashMap<>());
  }

  public MapCache(int initialCapacity) {
    this(new HashMap<>(initialCapacity));
  }

  /**
   * @param mapping allows to define your own map implementation
   */
  public MapCache(Map<Key, Value> mapping) {
    this.mapping = mapping;
    this.mappingFunction = null;
  }

  public MapCache(Function<Key, Value> mappingFunction) {
    this(new HashMap<>(), mappingFunction);
  }

  /**
   * @param mapping allows to define your own map implementation
   */
  public MapCache(Map<Key, Value> mapping, @Nullable Function<Key, Value> mappingFunction) {
    this.mapping = mapping;
    this.mappingFunction = mappingFunction;
  }

  /**
   * If the specified key is not already associated with a value (or is mapped
   * to {@code null}), attempts to compute its value using the given mapping
   * function and enters it into this map unless {@code null}.
   * <p>
   * High performance way
   * </p>
   *
   * @param key key with which the specified value is to be associated
   * @param param createValue's param
   * @return the current (existing or computed) value associated with
   * the specified key, should never {@code null}
   * @see #createValue
   */
  public final Value get(Key key, Param param) {
    Value value = mapping.get(key);
    if (value == null) {
      synchronized(mapping) {
        value = mapping.get(key);
        if (value == null) {
          value = createValue(key, param);
          Assert.state(value != null, "createValue() returns null");
          mapping.put(key, value);
        }
      }
    }
    return value;
  }

  /**
   * If the specified key is not already associated with a value (or is mapped
   * to {@code null}), attempts to compute its value using the given mapping
   * function and enters it into this map unless {@code null}.
   *
   * @param key key with which the specified value is to be associated
   * @return the current (existing or computed) value associated with
   * the specified key, or null if the computed value is null
   */
  @Nullable
  public final Value get(Key key) {
    return get(key, mappingFunction);
  }

  /**
   * If the specified key is not already associated with a value (or is mapped
   * to {@code null}), attempts to compute its value using the given mapping
   * function and enters it into this map unless {@code null}.
   *
   * @param key key with which the specified value is to be associated
   * @param mappingFunction the function to compute a value, can be null,
   * if its null use default mappingFunction
   * @return the current (existing or computed) value associated with
   * the specified key, or null if the computed value is null
   */
  @Nullable
  @SuppressWarnings("unchecked")
  public final Value get(Key key, @Nullable Function<Key, Value> mappingFunction) {
    Value value = mapping.get(key);
    if (value == null) {
      synchronized(mapping) {
        value = mapping.get(key);
        if (value == null) {
          if (mappingFunction == null) {
            mappingFunction = this.mappingFunction;
          }
          if (mappingFunction != null) {
            value = mappingFunction.apply(key);
          }
          else {
            // fallback to #createValue()
            value = createValue(key, null);
          }
          if (value == null) {
            value = (Value) NullValue.INSTANCE;
          }
          mapping.put(key, value);
        }
      }
    }
    return unwrap(value);
  }

  @Nullable
  protected Value createValue(Key key, Param param) {
    return null;
  }

  @Nullable
  public Value put(Key key, @Nullable Value value) {
    synchronized(mapping) {
      return unwrap(mapping.put(key, value));
    }
  }

  public void clear() {
    synchronized(mapping) {
      mapping.clear();
    }
  }

  @Nullable
  public Value remove(Key key) {
    synchronized(mapping) {
      return unwrap(mapping.remove(key));
    }
  }

  @Nullable
  private static <V> V unwrap(@Nullable V ret) {
    return ret == NullValue.INSTANCE ? null : ret;
  }

}
