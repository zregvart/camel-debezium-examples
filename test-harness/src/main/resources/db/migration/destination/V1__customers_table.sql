--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE
	TABLE
		customers(
			id INTEGER NOT NULL,
			first_name VARCHAR(100) NOT NULL,
			last_name VARCHAR(100) NOT NULL,
			email VARCHAR(100) NOT NULL,
			CONSTRAINT customers_pk PRIMARY KEY(id)
		);