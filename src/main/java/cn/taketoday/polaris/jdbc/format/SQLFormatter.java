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

package cn.taketoday.polaris.jdbc.format;

/**
 * Formatter contract
 *
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @author Steve Ebersole
 * @since 4.0 2022/9/12 19:20
 */
public interface SQLFormatter {
  String WHITESPACE = " \n\r\f\t";

  /**
   * Format the source SQL string.
   *
   * @param source The original SQL string
   * @return The formatted version
   */
  String format(String source);
}
