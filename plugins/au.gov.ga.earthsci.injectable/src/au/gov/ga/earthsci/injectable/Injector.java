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

import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.RegistryFactory;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.di.InjectorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.gov.ga.earthsci.common.collection.ArrayListTreeMap;
import au.gov.ga.earthsci.common.collection.ListSortedMap;
import au.gov.ga.earthsci.common.util.ExtensionPointHelper;

/**
 * Helper class for reading extensions that extend the injectable extension
 * point, and injects them into a provided {@link IEclipseContext}.
 * 
 * @author Michael de Hoog (michael.dehoog@ga.gov.au)
 */
public final class Injector
{
	private static final String INJECTOR_ID = "au.gov.ga.earthsci.injectables"; //$NON-NLS-1$
	private static final Logger logger = LoggerFactory.getLogger(Injector.class);

	private Injector()
	{
	}

	public static void injectIntoContext(IEclipseContext context)
	{
		//first sort the configuration elements by priority, descending
		IConfigurationElement[] config = RegistryFactory.getRegistry().getConfigurationElementsFor(INJECTOR_ID);
		ListSortedMap<Integer, IConfigurationElement> sorted =
				new ArrayListTreeMap<Integer, IConfigurationElement>(new Comparator<Integer>()
				{
					@Override
					public int compare(Integer first, Integer second)
					{
						//reverse the order:
						return -first.compareTo(second);
					}
				});
		for (IConfigurationElement element : config)
		{
			int priority = ExtensionPointHelper.getIntegerForProperty(element, "priority", 0); //$NON-NLS-1$
			sorted.putSingle(priority, element);
		}

		//next iterate through them in sorted order
		for (List<IConfigurationElement> elements : sorted.values())
		{
			for (IConfigurationElement element : elements)
			{
				try
				{
					boolean bind = "bind".equals(element.getName()); //$NON-NLS-1$
					boolean inject = "inject".equals(element.getName()); //$NON-NLS-1$
					boolean injectable = "injectable".equals(element.getName()); //$NON-NLS-1$
					if (bind)
					{
						Class<?> implementationClass = ExtensionPointHelper.getClassForProperty(element, "class"); //$NON-NLS-1$
						Class<?> bindingClass = ExtensionPointHelper.getClassForProperty(element, "binding"); //$NON-NLS-1$
						InjectorFactory.getDefault().addBinding(bindingClass).implementedBy(implementationClass);
					}
					else if (inject || injectable)
					{
						Object object = element.createExecutableExtension("class"); //$NON-NLS-1$
						ContextInjectionFactory.inject(object, context);
						if (inject)
						{
							IConfigurationElement[] types = element.getChildren("type"); //$NON-NLS-1$
							for (IConfigurationElement type : types)
							{
								@SuppressWarnings("unchecked")
								Class<Object> c =
										(Class<Object>) ExtensionPointHelper.getClassForProperty(type, "class"); //$NON-NLS-1$
								context.set(c, c.cast(object));
							}
							IConfigurationElement[] names = element.getChildren("name"); //$NON-NLS-1$
							for (IConfigurationElement name : names)
							{
								String n = name.getAttribute("value"); //$NON-NLS-1$
								context.set(n, object);
							}
							if (types.length == 0 && names.length == 0)
							{
								@SuppressWarnings("unchecked")
								Class<Object> c = (Class<Object>) object.getClass();
								context.set(c, object);
							}
						}
					}
					else
					{
						throw new IllegalArgumentException("Unknown injectable child: " + element.getName()); //$NON-NLS-1$
					}
				}
				catch (Exception e)
				{
					logger.error("Error processing injectable", e); //$NON-NLS-1$
				}
			}
		}
	}
}
