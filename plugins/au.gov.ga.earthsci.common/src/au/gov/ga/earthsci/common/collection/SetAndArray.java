/*******************************************************************************
 * Copyright 2012 Geoscience Australia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package au.gov.ga.earthsci.common.collection;

import java.util.Set;

/**
 * Set that maintains an array of the elements for quick access.
 * 
 * @author Michael de Hoog (michael.dehoog@ga.gov.au)
 */
public interface SetAndArray<E> extends Set<E>
{
	/**
	 * The array of elements. This array is lazily created. The returned array
	 * is the same until this Set is modified, in which case it is recreated
	 * next time this method is called.
	 * 
	 * @return The stored array of elements.
	 */
	E[] getArray(Class<E> type);
}
