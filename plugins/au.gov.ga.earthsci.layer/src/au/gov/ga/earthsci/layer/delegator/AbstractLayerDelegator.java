/*******************************************************************************
 * Copyright 2013 Geoscience Australia
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
package au.gov.ga.earthsci.layer.delegator;

import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.event.Message;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.render.DrawContext;

import java.awt.Point;
import java.beans.IntrospectionException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.gov.ga.earthsci.common.util.AbstractPropertyChangeBean;
import au.gov.ga.earthsci.common.util.IPropertyChangeBean;

/**
 * Abstract {@link Layer} implementation that delegates methods to another
 * {@link Layer} instance.
 * <p/>
 * Also implements {@link IPropertyChangeBean}. All setters will fire a property
 * change. Any changed properties (ie opacity, name, etc) will be recorded, and
 * set on any new layers passed to the {@link #setLayer(Layer)} method.
 * 
 * @author Michael de Hoog (michael.dehoog@ga.gov.au)
 */
public abstract class AbstractLayerDelegator<L extends Layer> extends AbstractPropertyChangeBean implements
		ILayerDelegator<L>
{
	private static final Logger logger = LoggerFactory.getLogger(AbstractLayerDelegator.class);

	private final PropertyChangeListener propertyChangeListener = new PropertyChangeListener()
	{
		@Override
		public void propertyChange(PropertyChangeEvent evt)
		{
			firePropertyChange(new PropertyChangeEvent(AbstractLayerDelegator.this, evt.getPropertyName(),
					evt.getOldValue(), evt.getNewValue()));
		}
	};

	private L layer = createDummyLayer();
	private Set<String> propertiesChanged = new HashSet<String>();
	private boolean propertiesChangedTracking = true;
	private final Object layerSemaphore = new Object();

	/**
	 * @return A dummy layer that does nothing; used for storage of property
	 *         values before the real layer is set on this delegator
	 */
	protected abstract L createDummyLayer();

	/**
	 * Is the given layer an instance of a dummy layer that would be created by
	 * the {@link #createDummyLayer()} method.
	 * 
	 * @param layer
	 *            Layer to test
	 * @return True if the given layer is a dummy layer
	 */
	protected abstract boolean isDummyLayer(L layer);

	@Override
	public L getLayer()
	{
		return layer;
	}

	@Override
	public void setLayer(L layer)
	{
		if (layer == null)
		{
			throw new NullPointerException("Layer delegate is null"); //$NON-NLS-1$
		}
		if (layer == this)
		{
			throw new IllegalArgumentException("Cannot delegate to itself"); //$NON-NLS-1$
		}

		Layer oldValue;
		synchronized (layerSemaphore)
		{
			oldValue = getLayer();
			copyProperties(oldValue, layer);
			this.layer.removePropertyChangeListener(propertyChangeListener);
			this.layer = layer;
			this.layer.addPropertyChangeListener(propertyChangeListener);
		}
		firePropertyChange("layer", oldValue, layer); //$NON-NLS-1$
	}

	@Override
	public Layer getGrandLayer()
	{
		Layer layer = getLayer();
		if (layer instanceof ILayerDelegator)
		{
			ILayerDelegator<?> delegator = (ILayerDelegator<?>) layer;
			return delegator.getGrandLayer();
		}
		return layer;
	}

	@Override
	public boolean isLayerSet()
	{
		return !isDummyLayer(layer);
	}

	@Override
	public boolean isGrandLayerSet()
	{
		if (layer instanceof ILayerDelegator<?>)
		{
			return ((ILayerDelegator<?>) layer).isGrandLayerSet();
		}
		return isLayerSet();
	}

	/**
	 * Copy the changed properties between layers, by calling the getters of the
	 * from layer and the setters on the to layer.
	 * 
	 * @param from
	 *            Layer to get property values from
	 * @param to
	 *            Layer to set property values on
	 */
	private void copyProperties(Layer from, Layer to)
	{
		if (from == to)
		{
			return;
		}

		synchronized (propertiesChanged)
		{
			setPropertiesChangedTrackingEnabled(false);
			for (String property : propertiesChanged)
			{
				try
				{
					PropertyDescriptor fromPropertyDescriptor = new PropertyDescriptor(property, from.getClass());
					PropertyDescriptor toPropertyDescriptor = new PropertyDescriptor(property, to.getClass());
					Method getter = fromPropertyDescriptor.getReadMethod();
					Method setter = toPropertyDescriptor.getWriteMethod();
					Object value = getter.invoke(from);
					setter.invoke(to, value);
				}
				catch (IntrospectionException e)
				{
					//ignore (invalid property name)
				}
				catch (Exception e)
				{
					logger.error("Error copying value between layers for property: " + property, e); //$NON-NLS-1$
				}
			}
			setPropertiesChangedTrackingEnabled(true);
		}
	}

	@Override
	public void firePropertyChange(PropertyChangeEvent propertyChangeEvent)
	{
		super.firePropertyChange(propertyChangeEvent);
		addChangedProperty(propertyChangeEvent.getPropertyName());
	}

	@Override
	public void firePropertyChange(String propertyName, Object oldValue, Object newValue)
	{
		super.firePropertyChange(propertyName, oldValue, newValue);
		addChangedProperty(propertyName);
	}

	public void setPropertiesChangedTrackingEnabled(boolean enabled)
	{
		propertiesChangedTracking = enabled;
	}

	private void addChangedProperty(String propertyName)
	{
		synchronized (propertiesChanged)
		{
			if (propertiesChangedTracking && !"layer".equals(propertyName)) //$NON-NLS-1$
			{
				propertiesChanged.add(propertyName);
			}
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt)
	{
		firePropertyChange(evt);
	}

	//////////////////////
	// Layer delegation //
	//////////////////////

	@Override
	public void dispose()
	{
		layer.dispose();
	}

	@Override
	public void onMessage(Message msg)
	{
		layer.onMessage(msg);
	}

	@Override
	public Object setValue(String key, Object value)
	{
		return layer.setValue(key, value);
	}

	@Override
	public boolean isEnabled()
	{
		return layer.isEnabled();
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		boolean oldValue = isEnabled();
		layer.setEnabled(enabled);
		firePropertyChange("enabled", oldValue, enabled); //$NON-NLS-1$
	}

	@Override
	public String getName()
	{
		return layer.getName();
	}

	@Override
	public void setName(String name)
	{
		String oldValue = getName();
		layer.setName(name);
		firePropertyChange("name", oldValue, name); //$NON-NLS-1$
	}

	@Override
	public AVList setValues(AVList avList)
	{
		return layer.setValues(avList);
	}

	@Override
	public String getRestorableState()
	{
		return layer.getRestorableState();
	}

	@Override
	public double getOpacity()
	{
		return layer.getOpacity();
	}

	@Override
	public void restoreState(String stateInXml)
	{
		layer.restoreState(stateInXml);
	}

	@Override
	public Object getValue(String key)
	{
		return layer.getValue(key);
	}

	@Override
	public void setOpacity(double opacity)
	{
		double oldValue = getOpacity();
		layer.setOpacity(opacity);
		firePropertyChange("opacity", oldValue, opacity); //$NON-NLS-1$
	}

	@Override
	public Collection<Object> getValues()
	{
		return layer.getValues();
	}

	@Override
	public String getStringValue(String key)
	{
		return layer.getStringValue(key);
	}

	@Override
	public boolean isPickEnabled()
	{
		return layer.isPickEnabled();
	}

	@Override
	public Set<Entry<String, Object>> getEntries()
	{
		return layer.getEntries();
	}

	@Override
	public boolean hasKey(String key)
	{
		return layer.hasKey(key);
	}

	@Override
	public Object removeKey(String key)
	{
		return layer.removeKey(key);
	}

	@Override
	public void setPickEnabled(boolean isPickable)
	{
		boolean oldValue = isPickEnabled();
		layer.setPickEnabled(isPickable);
		firePropertyChange("pickEnabled", oldValue, isPickable); //$NON-NLS-1$
	}

	@Override
	public void preRender(DrawContext dc)
	{
		layer.preRender(dc);
	}

	@Override
	public void render(DrawContext dc)
	{
		layer.render(dc);
	}

	@Override
	public void pick(DrawContext dc, Point pickPoint)
	{
		layer.pick(dc, pickPoint);
	}

	@Override
	public boolean isAtMaxResolution()
	{
		return layer.isAtMaxResolution();
	}

	@Override
	public boolean isMultiResolution()
	{
		return layer.isMultiResolution();
	}

	@Override
	public double getScale()
	{
		return layer.getScale();
	}

	@Override
	public boolean isNetworkRetrievalEnabled()
	{
		return layer.isNetworkRetrievalEnabled();
	}

	@Override
	public void setNetworkRetrievalEnabled(boolean networkRetrievalEnabled)
	{
		boolean oldValue = isNetworkRetrievalEnabled();
		layer.setNetworkRetrievalEnabled(networkRetrievalEnabled);
		firePropertyChange("networkRetrievalEnabled", oldValue, networkRetrievalEnabled); //$NON-NLS-1$
	}

	@Override
	public AVList copy()
	{
		return layer.copy();
	}

	@Override
	public void setExpiryTime(long expiryTime)
	{
		long oldValue = getExpiryTime();
		layer.setExpiryTime(expiryTime);
		firePropertyChange("expiryTime", oldValue, expiryTime); //$NON-NLS-1$
	}

	@Override
	public AVList clearList()
	{
		return layer.clearList();
	}

	@Override
	public long getExpiryTime()
	{
		return layer.getExpiryTime();
	}

	@Override
	public double getMinActiveAltitude()
	{
		return layer.getMinActiveAltitude();
	}

	@Override
	public void setMinActiveAltitude(double minActiveAltitude)
	{
		double oldValue = getMinActiveAltitude();
		layer.setMinActiveAltitude(minActiveAltitude);
		firePropertyChange("minActiveAltitude", oldValue, minActiveAltitude); //$NON-NLS-1$
	}

	@Override
	public double getMaxActiveAltitude()
	{
		return layer.getMaxActiveAltitude();
	}

	@Override
	public void setMaxActiveAltitude(double maxActiveAltitude)
	{
		double oldValue = getMaxActiveAltitude();
		layer.setMaxActiveAltitude(maxActiveAltitude);
		firePropertyChange("maxActiveAltitude", oldValue, maxActiveAltitude); //$NON-NLS-1$
	}

	@Override
	public boolean isLayerInView(DrawContext dc)
	{
		return layer.isLayerInView(dc);
	}

	@Override
	public boolean isLayerActive(DrawContext dc)
	{
		return layer.isLayerActive(dc);
	}

	@Override
	public Double getMaxEffectiveAltitude(Double radius)
	{
		return layer.getMaxEffectiveAltitude(radius);
	}

	@Override
	public Double getMinEffectiveAltitude(Double radius)
	{
		return layer.getMinEffectiveAltitude(radius);
	}
}
