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
package au.gov.ga.earthsci.injectable;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.spi.RegistryContributor;
import org.osgi.framework.Bundle;

/**
 * Helper class for accessing classes defined in extention point definitions.
 * 
 * @author Michael de Hoog (michael.dehoog@ga.gov.au)
 */
public class ExtensionClassLoader
{
	/**
	 * Load the class defined in the extension point configuration element under
	 * the given property name.
	 * 
	 * @param element
	 *            Extension point configuration element
	 * @param propertyName
	 *            Property name in element that defines the class name
	 * @return Class defined in the configuration element property
	 * @throws ClassNotFoundException
	 *             If the class could not be found
	 */
	public static Class<?> getClassForProperty(IConfigurationElement element, String propertyName)
			throws ClassNotFoundException
	{
		String className = element.getAttribute(propertyName);
		return getClassForName(element, className);
	}

	/**
	 * Load the named class defined in the given extension point configuration
	 * element.
	 * 
	 * @param element
	 *            Extension point configuration element
	 * @param className
	 *            Fully qualified class name, passed to
	 *            {@link Bundle#loadClass(String)}
	 * @return Class for the given name
	 * @throws ClassNotFoundException
	 *             If the class could not be found
	 */
	public static Class<?> getClassForName(IConfigurationElement element, String className)
			throws ClassNotFoundException
	{
		IContributor contributor = element.getContributor();
		if (contributor instanceof RegistryContributor)
		{
			String stringId = ((RegistryContributor) contributor).getId();
			long id = Long.parseLong(stringId);
			Bundle bundle = Activator.getContext().getBundle(id);
			return bundle.loadClass(className);
		}
		throw new ClassNotFoundException(className);
	}
}
