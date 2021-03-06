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
<#import "insert.ftl" as i>
<#import "merge.ftl" as m>
<#import "update.ftl" as u>
<#import "delete.ftl" as d>
<#switch body['op']>
  <#case 'c'>
    <@i.insert body['source']['table'] body['after']?keys />
    <#break>
  <#case 'r'>
    <@m.merge body['source']['table'] body['after']?keys />
  <#break>
  <#case 'u'>
    <@u.update body['source']['table'] headers['kafka.KEY'] body['after']?keys />
    <#break>
  <#case 'd'>
    <@d.delete body['source']['table'] headers['kafka.KEY'] />
    <#break>
</#switch>