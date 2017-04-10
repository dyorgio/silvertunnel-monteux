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

package cf.monteux.silvertunnel.netlib.layer.socks;

import java.io.IOException;
import java.util.Map;

import cf.monteux.silvertunnel.netlib.api.NetAddress;
import cf.monteux.silvertunnel.netlib.api.NetAddressNameService;
import cf.monteux.silvertunnel.netlib.api.NetLayer;
import cf.monteux.silvertunnel.netlib.api.NetLayerStatus;
import cf.monteux.silvertunnel.netlib.api.NetServerSocket;
import cf.monteux.silvertunnel.netlib.api.NetSocket;

/**
 * NetLayer that implements a Socks4/Socks5 server.
 * 
 * This is a very simple implementation (without authentication, without server
 * socket handling).
 * 
 * @author hapke
 */
public class SocksServerNetLayer implements NetLayer
{
	private final NetLayer lowerNetLayer;

	/**
	 * @param lowerNetLayer
	 *            layer that should be compatible to TcpipNetLayer, i.e. it
	 *            should accept TcpipNetAddress objects to create sockets
	 */
	public SocksServerNetLayer(final NetLayer lowerNetLayer)
	{
		this.lowerNetLayer = lowerNetLayer;
	}

	/**
	 * Create a Socks4/Socks5 server that receives the socks commands on the
	 * returned NetSocket an executes these commands on the lowerNetLayer.
	 * 
	 * @see NetLayer#createNetSocket(Map, NetAddress, NetAddress)
	 * 
	 * @param localProperties
	 *            will be ignored
	 * @param localAddress
	 *            will be ignored
	 * @param remoteAddress
	 *            will be ignored
	 */
	@Override
	public NetSocket createNetSocket(final Map<String, Object> localProperties,
									 final NetAddress localAddress, 
									 final NetAddress remoteAddress)
			throws IOException
	{
		return new SocksServerNetSession(lowerNetLayer, localProperties,
				localAddress, remoteAddress).createHigherLayerNetSocket();
	}

	/**
	 * Not implemented.
	 * 
	 * @see NetLayer#createNetServerSocket(Map, NetAddress)
	 * 
	 * @throws UnsupportedOperationException
	 */
	@Override
	public NetServerSocket createNetServerSocket(final Map<String, Object> properties, 
												 final NetAddress localListenAddress)
	{
		throw new UnsupportedOperationException();
	}

	/** @see NetLayer#getStatus() */
	@Override
	public NetLayerStatus getStatus()
	{
		return NetLayerStatus.READY;
	}

	/** @see NetLayer#waitUntilReady() */
	@Override
	public void waitUntilReady()
	{
		// nothing to do
	}

	/** @see NetLayer#clear() */
	@Override
	public void clear() throws IOException
	{
		// nothing to do
	}

	/** @see NetLayer#getNetAddressNameService() */
	@Override
	public NetAddressNameService getNetAddressNameService()
	{
		throw new UnsupportedOperationException();
	}
}
