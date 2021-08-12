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
		debezium_signal(
			id VARCHAR(42) NOT NULL DEFAULT gen_random_uuid(),
			type VARCHAR(32) NOT NULL,
			data VARCHAR(2048) NULL,
			CONSTRAINT debezium_signal_pk PRIMARY KEY(id)
		);

ALTER TABLE debezium_signal REPLICA IDENTITY FULL;