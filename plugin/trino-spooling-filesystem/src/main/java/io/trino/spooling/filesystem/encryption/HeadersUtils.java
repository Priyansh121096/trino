/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.spooling.filesystem.encryption;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

public class HeadersUtils
{
    private HeadersUtils() {}

    public static String getOnlyHeader(Map<String, List<String>> headers, String name)
    {
        List<String> values = headers.get(name);
        checkArgument(values != null && !values.isEmpty(), "Required header " + name + " was not found");
        checkArgument(values.size() == 1, "Required header " + name + " contains more than one value");
        return values.getFirst();
    }
}
