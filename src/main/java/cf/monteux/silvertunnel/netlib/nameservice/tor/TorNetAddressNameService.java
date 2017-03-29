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

package cf.monteux.silvertunnel.netlib.nameservice.tor;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;

import cf.monteux.silvertunnel.netlib.api.NetAddress;
import cf.monteux.silvertunnel.netlib.api.NetAddressNameService;
import cf.monteux.silvertunnel.netlib.api.util.IpNetAddress;
import cf.monteux.silvertunnel.netlib.layer.tor.clientimpl.Tor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NetAddressNameService that resolves queries through the Tor anonymity
 * network. The DNS requests are sent and received by a tor server.
 * 
 * To get an instance use TorNetLayer.getTorNetAddressNameService().
 * 
 * @author hapke
 */
public class TorNetAddressNameService implements NetAddressNameService
{
	/** */
	private static final Logger LOG = LoggerFactory.getLogger(TorNetAddressNameService.class);

	/** {@link Tor} instance used for communications. */
	private final Tor tor;

	/**
	 * This constructor is only for internal use.
	 * 
	 * To get an instance use TorNetLayer.getTorNetAddressNameService().
	 * 
	 * @param tor
	 *            the internally used Tor instance
	 */
	protected TorNetAddressNameService(final Tor tor)
	{
		// simple check
		if (tor == null)
		{
			throw new NullPointerException("invalid argument tor=null");
		}

		this.tor = tor;
	}

	/** @see NetAddressNameService#getAddressesByName(String) */
	@Override
	public NetAddress[] getAddressesByName(final String hostname) throws UnknownHostException
	{
		try
		{
			checkNetlibTorLoop();

			// resolve host name -> IP
			final List<NetAddress> result = tor.resolveAll(hostname);
			return result.toArray(new NetAddress[result.size()]);

		}
		catch (final UnknownHostException e)
		{
			throw e;

		}
		catch (final IOException e)
		{
			final UnknownHostException e2 = new UnknownHostException("Error with hostname=" + hostname);
			e2.initCause(e);
			throw e2;
		}
        catch (final Throwable throwable) {
            LOG.warn("got Exception",throwable);
            return null;
        }
	}

	/** @see NetAddressNameService#getNamesByAddress(NetAddress) */
	@Override
	public String[] getNamesByAddress(final NetAddress netAddress) throws UnknownHostException
	{
		try
		{
			checkNetlibTorLoop();

			if (netAddress == null)
			{
				throw new UnknownHostException("Invalid netAddress=null");
			}
			else if (netAddress instanceof IpNetAddress)
			{
				// resolve IP -> host name
				final IpNetAddress ipNetAddress = (IpNetAddress) netAddress;
				final String result = tor.resolve(ipNetAddress);
				return new String[] { result };

			}
			else
			{
				// error
				throw new UnknownHostException("Invalid type of netAddress=" + netAddress);
			}

		}
		catch (final UnknownHostException e)
		{
			throw e;

		}
		catch (final IOException e)
		{
			final UnknownHostException e2 = new UnknownHostException("Error with netAddress=" + netAddress);
			e2.initCause(e);
			throw e2;
		}
        catch (final Throwable throwable)
        {
            LOG.warn("got Exception", throwable);
            return null;
        }
	}

	/**
	 * This method is called to avoid loops in the sense: Netlib Tor(NetLayer)
	 * calls FOO call InetAddress calls TorNetAddressNameService Such a look can
	 * lead to a dead lock and must be provided.
	 * 
	 * @throws UnknownHostException
	 *             if this method is called (indirectly from Netlib Tor)
	 */
	private void checkNetlibTorLoop() throws UnknownHostException
	{
		final UnknownHostException e = new UnknownHostException("Netlib Tor call cycle / dead lock prevented");
		for (final StackTraceElement ste : e.getStackTrace())
		{
			if (ste.getClassName().startsWith("com.rovemonteux.silvertunnel.netlib.layer.tor."))
			{
				// this is a loop / call cycle / dead lock - stop here and throw
				// the exception to avoid blocking
				if (LOG.isDebugEnabled())
				{
					LOG.debug("Netlib Tor call cycle / dead lock prevented - right now", e);
				}
				else
				{
					LOG.info("Netlib Tor call cycle / dead lock prevented - right now; use FINE logging to see the call stack");
				}
				throw e;
			}
		}
	}
}
