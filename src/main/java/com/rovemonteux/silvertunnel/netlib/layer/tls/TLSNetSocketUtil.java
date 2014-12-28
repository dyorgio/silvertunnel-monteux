/*
 * silvertunnel.org Netlib - Java library to easily access anonymity networks
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

package com.rovemonteux.silvertunnel.netlib.layer.tls;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import com.rovemonteux.silvertunnel.netlib.api.NetSocket;
import com.rovemonteux.silvertunnel.netlib.api.impl.NetSocket2Socket;
import com.rovemonteux.silvertunnel.netlib.api.impl.Socket2NetSocket;
import com.rovemonteux.silvertunnel.netlib.api.util.TcpipNetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper method to access the Java-internal TLS/SSL logic.
 * 
 * @author hapke
 */
public class TLSNetSocketUtil
{
	/** */
	private static final Logger LOG = LoggerFactory.getLogger(TLSNetSocketUtil.class);

	/**
	 * Returns a socket layered over an existing socket connected to the named
	 * host, at the given port. This construction can be used when tunneling
	 * SSL/TLS through a proxy or when negotiating the use of SSL/TLS over an
	 * existing socket. The host and port refer to the logical peer destination.
	 * 
	 * @param lowerLayerNetSocket
	 * @param remoteAddress
	 * @param autoClose
	 * @param enabledCipherSuites
	 *            if null, the default TLS cipher suites are used
	 * @param keyManagers
	 *            if null, the now local keys are used
	 * @param trustManagers
	 *            if null, the default trust managers are used
	 * @return
	 * @throws IOException
	 */
	public static NetSocket createTLSSocket(NetSocket lowerLayerNetSocket,
			TcpipNetAddress remoteAddress, boolean autoClose,
			String[] enabledCipherSuites, KeyManager[] keyManagers,
			TrustManager[] trustManagers) throws IOException
	{
		final Socket lowerLayerSocket = new NetSocket2Socket(
				lowerLayerNetSocket);

		// create TLS/SSL socket factory
		SSLContext context = null;
		try
		{
			context = SSLContext.getInstance("TLS", "SunJSSE");
			context.init(keyManagers, trustManagers, null);
		}
		catch (final NoSuchAlgorithmException e)
		{
			final IOException ioe = new IOException();
			ioe.initCause(e);
			throw ioe;
		}
		catch (final KeyManagementException e)
		{
			final IOException ioe = new IOException();
			ioe.initCause(e);
			throw ioe;
		}
		catch (final NoSuchProviderException e)
		{
			final IOException ioe = new IOException();
			ioe.initCause(e);
			throw ioe;
		}
		final SSLSocketFactory f = context.getSocketFactory();

		// create TLS/SSL session with socket
		final String hostname = (remoteAddress != null) ? remoteAddress
				.getHostname() : null;
		final int port = (remoteAddress != null) ? remoteAddress.getPort() : 0;
		final SSLSocket resultSocket = (SSLSocket) f.createSocket(
				lowerLayerSocket, hostname, port, autoClose);

		// set properties
		if (LOG.isDebugEnabled())
		{
			LOG.debug("default enabledCipherSuites="
				+ Arrays.toString(resultSocket.getEnabledCipherSuites()));
		}
		if (enabledCipherSuites != null)
		{
			resultSocket.setEnabledCipherSuites(enabledCipherSuites);
			if (LOG.isDebugEnabled())
			{
				LOG.debug("set enabledCipherSuites="
					+ Arrays.toString(enabledCipherSuites));
			}
		}

		return new TLSNetSocket(new Socket2NetSocket(resultSocket),
				resultSocket.getSession(), "" + lowerLayerNetSocket);
	}
}
