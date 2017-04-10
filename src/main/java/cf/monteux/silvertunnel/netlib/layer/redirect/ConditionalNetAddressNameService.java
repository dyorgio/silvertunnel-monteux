/*
 * SilverTunnel-Monteux Netlib - Java library to easily access anonymity networks
 * Copyright (c) 2009-2012 silvertunnel.org
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

package cf.monteux.silvertunnel.netlib.layer.redirect;

import java.net.UnknownHostException;

import cf.monteux.silvertunnel.netlib.api.NetAddress;
import cf.monteux.silvertunnel.netlib.api.NetAddressNameService;

/**
 * Implementation of NetAddressNameService that belongs to ConditionalNetLayer.
 * 
 * @author hapke
 */
public class ConditionalNetAddressNameService implements NetAddressNameService
{
	private final ConditionalNetLayer conditionalNetLayer;

	protected ConditionalNetAddressNameService(final ConditionalNetLayer conditionalNetLayer)
	{
		this.conditionalNetLayer = conditionalNetLayer;
	}

	/**
	 * @see NetAddressNameService#getAddressesByName(String)
	 * 
	 * @param name
	 *            host name to lookup
	 * @return one or more addresses that match; ordered by relevance, i.e.
	 *         prefer to use the first element of the array
	 * @throws UnknownHostException
	 *             if the resolution failed
	 * @throws UnsupportedOperationException
	 *             if the operation could not be executed (because of internal
	 *             errors)
	 */
	@Override
	public NetAddress[] getAddressesByName(final String name)
			throws UnknownHostException
	{
		return conditionalNetLayer.getMatchingNetLayer(name).getNetAddressNameService().getAddressesByName(name);
	}

	/**
	 * @see NetAddressNameService#getNamesByAddress(cf.monteux.silvertunnel.netlib.api.NetAddress)
	 * 
	 * @param address
	 *            IP address to lookup
	 * @return the host name that matches (array is always of size 1)
	 * @throws UnknownHostException
	 *             if the resolution failed
	 * @throws UnsupportedOperationException
	 *             if the operation could not be executed (because of internal
	 *             errors)
	 */
	@Override
	public String[] getNamesByAddress(final NetAddress address)
			throws UnknownHostException
	{
		return conditionalNetLayer.getMatchingNetLayer(address).getNetAddressNameService().getNamesByAddress(address);
	}
}
