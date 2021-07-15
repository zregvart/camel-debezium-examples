/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package data;

import java.util.Map;
import java.util.Objects;

public final class Customer {
	public final String email;

	public final String firstName;

	public final int id;

	public final String lastName;

	public Customer(final int id, final String firstName, final String lastName, final String email) {
		this.id = id;
		this.firstName = firstName;
		this.lastName = lastName;
		this.email = email;
	}

	public Customer(final Map<String, String> entry) {
		this(Integer.parseInt(entry.get("id")), entry.get("first_name"), entry.get("last_name"), entry.get("email"));
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == this) {
			return true;
		}

		if (!(obj instanceof Customer)) {
			return false;
		}

		final Customer another = (Customer) obj;

		return Objects.equals(id, another.id)
		    && Objects.equals(firstName, another.firstName)
		    && Objects.equals(lastName, another.lastName)
		    && Objects.equals(email, another.email);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, firstName, lastName, email);
	}

	@Override
	public String toString() {
		return new StringBuilder("Customer: id: ").append(id)
		    .append(", firstName: ").append(firstName)
		    .append(", lastName: ").append(lastName)
		    .append(", email: ").append(email)
		    .toString();
	}
}