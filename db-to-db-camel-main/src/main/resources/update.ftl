<#--

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
<#macro update table key_map columns>
<#assign key_json = key_map?eval_json>
<#assign keys = key_json?keys>
UPDATE ${table} SET
<#list columns as column>
  ${column} = :?${column}<#sep>,
</#list>

WHERE
<#list keys as key>
  ${key} = :?${key}<#sep> AND </#sep><#rt>
</#list>
</#macro>