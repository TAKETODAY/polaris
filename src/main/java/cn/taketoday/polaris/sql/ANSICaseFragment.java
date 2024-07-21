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

/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package cn.taketoday.polaris.sql;

/**
 * An ANSI SQL CASE expression : {@code case when ... then ... end as ..}
 *
 * @author Gavin King
 * @author Simon Harris
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0
 */
public class ANSICaseFragment extends CaseFragment {

  @Override
  public String toFragmentString() {
    StringBuilder builder = new StringBuilder(cases.size() * 15 + 10)
            .append("case");

    for (var entry : cases.entrySet()) {
      builder.append(" when ")
              .append(entry.getKey())
              .append(" is not null then ")
              .append(entry.getValue());
    }

    builder.append(" end");
    if (returnColumnName != null) {
      builder.append(" as ")
              .append(returnColumnName);
    }

    return builder.toString();
  }

}
