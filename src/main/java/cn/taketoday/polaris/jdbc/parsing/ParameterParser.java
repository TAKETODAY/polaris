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

package cn.taketoday.polaris.jdbc.parsing;

import java.util.ArrayList;
import java.util.Map;

/**
 * Created by lars on 22.09.2014.
 */
public class ParameterParser extends CharParser {

  private final Map<String, cn.taketoday.polaris.jdbc.parsing.QueryParameter> parameterMap;
  int paramIdx = 1;

  public ParameterParser(Map<String, cn.taketoday.polaris.jdbc.parsing.QueryParameter> parameterMap) {
    this.parameterMap = parameterMap;
  }

  @Override
  public boolean supports(char c, String sql, int idx) {
    return sql.length() > idx + 1
            && c == ':'
            && Character.isJavaIdentifierStart(sql.charAt(idx + 1))
            && sql.charAt(idx - 1) != ':';
  }

  @Override
  public int parse(char c, int idx, StringBuilder parsedSql, String sql, int length) {
    int startIdx = idx;
    idx += 1;

    while (idx + 1 < length && Character.isJavaIdentifierPart(sql.charAt(idx + 1))) {
      idx += 1;
    }

    String name = sql.substring(startIdx + 1, idx + 1);
    cn.taketoday.polaris.jdbc.parsing.QueryParameter queryParameter = parameterMap.get(name);

    if (queryParameter == null) {
      queryParameter = new QueryParameter(name, cn.taketoday.polaris.jdbc.parsing.ParameterIndexHolder.valueOf(paramIdx));
      parameterMap.put(name, queryParameter);
    }
    else {
      // set ParameterApplier
      cn.taketoday.polaris.jdbc.parsing.ParameterIndexHolder indexHolder = queryParameter.getHolder();
      if (indexHolder == null) {
        indexHolder = cn.taketoday.polaris.jdbc.parsing.ParameterIndexHolder.valueOf(paramIdx);
        queryParameter.setHolder(indexHolder);
      }
      else if (indexHolder instanceof cn.taketoday.polaris.jdbc.parsing.ListParameterIndexApplier) {
        ((cn.taketoday.polaris.jdbc.parsing.ListParameterIndexApplier) indexHolder).addIndex(paramIdx);
      }
      else if (indexHolder instanceof cn.taketoday.polaris.jdbc.parsing.DefaultParameterIndexHolder) {
        ArrayList<Integer> indices = new ArrayList<>();
        final int index = ((cn.taketoday.polaris.jdbc.parsing.DefaultParameterIndexHolder) indexHolder).getIndex();
        indices.add(index);
        indices.add(paramIdx);
        queryParameter.setHolder(ParameterIndexHolder.valueOf(indices));
      }
    }

    paramIdx++;
    parsedSql.append('?');
    return idx;
  }
}
