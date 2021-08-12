#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

Feature: Data replication DB to DB
  Move data from a RDBMS (Postgres) to another database (MySQL) during
  application modernization (monolith to microservices migration,
  database migration etc)

Background: Example solution is deployed
  Given a running example

  @dml @create
  Scenario: New row in source database is replicated to the destination database
    When a row is inserted in the source database
      | id | first_name | last_name | email                |
      | 1  | John       | Doe       | john.doe@example.com |
    Then a row is present in the destination database
      | id | first_name | last_name | email                |
      | 1  | John       | Doe       | john.doe@example.com |

  @dml @update
  Scenario: Row updates in source database is replicated to the destination database
    When a row is updated in the source database
      | id | first_name | last_name | email                    |
      | 2  | Chuck      | Dowson    | chuck.dowson@example.com |
    Then an existing row is updated in the destination database
      | id | first_name | last_name | email                    |
      | 2  | Chuck      | Dowson    | chuck.dowson@example.com |

  @dml @delete
  Scenario: Row deletions in source database are replicated to the destination database
    When a row with the id of 3 deleted from the source database
    Then a row with the id of 3 doesn't exist in the destination database
