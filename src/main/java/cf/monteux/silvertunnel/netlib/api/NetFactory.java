/*
 * SilverTunnel-Monteux Netlib - Java library to easily access anonymity networks
 * Copyright (c) 2017 Rove Monteux
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */
/*
 * SilverTunnel-Monteux Netlib - Java library to easily access anonymity networks
 * Copyright (c) 2013 silvertunnel-ng.org
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package cf.monteux.silvertunnel.netlib.api;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;

/**
 * Manage a repository of NetLayer objects.
 * 
 * @author hapke
 * @author Tobias Boese
 * @author Rove Monteux
 */
public class NetFactory implements NetLayerFactory
{
	/** */
	private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger("NetFactory");

	public static final String NETFACTORY_MAPPING_PROPERTIES = "/cf/monteux/silvertunnel/netlib/api/netfactory_mapping.properties";

	/** repository cache. */
	private final Map<NetLayerIDs, NetLayer> netLayerRepository = new HashMap<NetLayerIDs, NetLayer>();

	private static NetFactory instance = new NetFactory();

	public static NetFactory getInstance()
	{
		return instance;
	}

	/**
	 * Registering a {@link NetLayer}.
	 * 
	 * @param netLayerId a {@link NetLayerIDs}
	 * @param netLayer the {@link NetLayer} to be registered
	 */
	public final synchronized void registerNetLayer(final NetLayerIDs netLayerId, final NetLayer netLayer)
	{
		netLayerRepository.put(netLayerId, netLayer);
		logger.debug("registerNetLayer with netLayerId={}", netLayerId);
	}

	/**
	 * Cleanup all registered NetLayers.
	 */
	public final synchronized void clearRegisteredNetLayers()
	{
		netLayerRepository.clear();
	}
	/**
	 * @see NetLayerFactory#getNetLayerById(cf.monteux.silvertunnel.netlib.api.NetLayerIDs)
	 */
	@Override
	public synchronized NetLayer getNetLayerById(final NetLayerIDs netLayerId)
	{
		NetLayer result = netLayerRepository.get(netLayerId);
		if (result == null)
		{
			// not yet in cache: try to instantiate
			try
			{
				final NetLayerFactory factory = getNetLayerFactoryByNetLayerID(netLayerId);
				if (factory != null)
				{
					result = factory.getNetLayerById(netLayerId);
					if (result != null)
					{
						// store in cache
						registerNetLayer(netLayerId, result);
					}
				}

			}
			catch (final Exception e)
			{
				logger.error("could not create NetLayer of {}", netLayerId, e);
			}
		}

		return result;
	}

	/**
	 * Load class based on mapping properties.
	 * 
	 * @param netLayerId
	 * @return null if not found
	 */
	private NetLayerFactory getNetLayerFactoryByNetLayerID(final NetLayerIDs netLayerId)
	{
		try
		{
			logger.info("Net layer "+netLayerId.getValue());
			// load properties, read class factory name
			final InputStream in = getClass().getResourceAsStream(NETFACTORY_MAPPING_PROPERTIES);
			final Properties mapping = new Properties();
			mapping.load(in);

			final String netLayerFactoryClassName = mapping.getProperty(netLayerId.getValue());

			// try to load NetLayerFactory
			final Class<?> clazz = Class.forName(netLayerFactoryClassName);
			final Constructor<?> c = clazz.getConstructor();
			final NetLayerFactory result = (NetLayerFactory) c.newInstance();

			return result;

		}
		catch (final Exception e)
		{
			logger.error("could not create NetLayerFactory of {}", netLayerId, e);
			return null;
		}
	}
}
