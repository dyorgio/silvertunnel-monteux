/*
 * Copyright 2001-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package cf.monteux.silvertunnel.netlib.adapter.url.impl.net.https;

import java.io.IOException;
import java.net.Proxy;
import java.net.SecureCacheResponse;
import java.net.URL;
import java.security.Principal;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;

import cf.monteux.silvertunnel.netlib.adapter.url.impl.net.http.Handler;
import cf.monteux.silvertunnel.netlib.adapter.url.impl.net.http.HttpClient;
import cf.monteux.silvertunnel.netlib.adapter.url.impl.net.http.HttpURLConnection;
import cf.monteux.silvertunnel.netlib.api.NetLayer;

/**
 * HTTPS URL connection support. We need this delegate because
 * HttpsURLConnection is a subclass of java.net.HttpURLConnection. We will avoid
 * copying over the code from sun.net.www.protocol.http.HttpURLConnection by
 * having this class
 * 
 */
public class HttpsURLConnection extends HttpURLConnection
{
	protected HttpsURLConnection(NetLayer lowerNetLayer, URL url,
			Handler handler) throws IOException
	{
		this(lowerNetLayer, url, null, handler);
	}

	protected HttpsURLConnection(NetLayer lowerNetLayer, URL url, Proxy p,
			Handler handler) throws IOException
	{
		super(lowerNetLayer, url, p, handler);
	}

	/**
	 * No user application is able to call these routines, as no one should ever
	 * get access to an instance of DelegateHttpsURLConnection (sun.* or com.*)
	 */

	/**
	 * Create a new HttpClient object, bypassing the cache of HTTP client
	 * objects/connections.
	 * 
	 * Note: this method is changed from protected to public because the
	 * com.sun.ssl.internal.www.protocol.https handler reuses this class for its
	 * actual implemantation
	 * 
	 * @param url
	 *            the URL being accessed
	 */
	@Override
	public void setNewClient(URL url) throws IOException
	{
		setNewClient(url, false);
	}

	/**
	 * Obtain a HttpClient object. Use the cached copy if specified.
	 * 
	 * Note: this method is changed from protected to public because the
	 * com.sun.ssl.internal.www.protocol.https handler reuses this class for its
	 * actual implemantation
	 * 
	 * @param url
	 *            the URL being accessed
	 * @param useCache
	 *            whether the cached connection should be used if present
	 */
	@Override
	public void setNewClient(URL url, boolean useCache) throws IOException
	{
		http = HttpsClient.New(lowerNetLayer, url,
		/* getHostnameVerifier() */null, useCache);
		// TODO: remove unneeded((HttpsClient)http).afterConnect();
	}

	/**
	 * Create a new HttpClient object, set up so that it uses per-instance
	 * proxying to the given HTTP proxy. This bypasses the cache of HTTP client
	 * objects/connections.
	 * 
	 * Note: this method is changed from protected to public because the
	 * com.sun.ssl.internal.www.protocol.https handler reuses this class for its
	 * actual implemantation
	 * 
	 * @param url
	 *            the URL being accessed
	 * @param proxyHost
	 *            the proxy host to use
	 * @param proxyPort
	 *            the proxy port to use
	 */
	@Override
	public void setProxiedClient(URL url, String proxyHost, int proxyPort)
			throws IOException
	{
		setProxiedClient(url, proxyHost, proxyPort, false);
	}

	/**
	 * Used by subclass to access "connected" variable.
	 */
	public boolean isConnected()
	{
		return connected;
	}

	/**
	 * Used by subclass to access "connected" variable.
	 */
	public void setConnected(boolean conn)
	{
		connected = conn;
	}

	/**
	 * Implements the HTTP protocol handler's "connect" method, establishing an
	 * SSL connection to the server as necessary.
	 */
	@Override
	public void connect() throws IOException
	{
		if (connected)
		{
			return;
		}
		plainConnect();
		if (cachedResponse != null)
		{
			// using cached response
			return;
		}
	}

	@Override
	protected HttpClient getNewHttpClient(URL url, Proxy p, int connectTimeout)
			throws IOException
	{
		return HttpsClient.New(lowerNetLayer, url, (HostnameVerifier) null,
				true, connectTimeout);
	}

	@Override
	protected HttpClient getNewHttpClient(URL url, Proxy p, int connectTimeout,
			boolean useCache) throws IOException
	{
		return HttpsClient.New(lowerNetLayer, url, (HostnameVerifier) null,
				useCache, connectTimeout);
	}

	/**
	 * Returns the cipher suite in use on this connection.
	 */
	public String getCipherSuite()
	{
		if (cachedResponse != null)
		{
			return ((SecureCacheResponse) cachedResponse).getCipherSuite();
		}
		if (http == null)
		{
			throw new IllegalStateException("connection not yet open");
		}
		else
		{
			return ((HttpsClient) http).getCipherSuite();
		}
	}

	/**
	 * Returns the certificate chain the client sent to the server, or null if
	 * the client did not authenticate.
	 */
	public java.security.cert.Certificate[] getLocalCertificates()
	{
		if (cachedResponse != null)
		{
			final List<java.security.cert.Certificate> l = ((SecureCacheResponse) cachedResponse)
					.getLocalCertificateChain();
			if (l == null)
			{
				return null;
			}
			else
			{
				return (java.security.cert.Certificate[]) l.toArray();
			}
		}
		if (http == null)
		{
			throw new IllegalStateException("connection not yet open");
		}
		else
		{
			return (((HttpsClient) http).getLocalCertificates());
		}
	}

	/**
	 * Returns the server's certificate chain, or throws SSLPeerUnverified
	 * Exception if the server did not authenticate.
	 */
	public java.security.cert.Certificate[] getServerCertificates()
			throws SSLPeerUnverifiedException
	{
		if (cachedResponse != null)
		{
			final List<java.security.cert.Certificate> l = ((SecureCacheResponse) cachedResponse)
					.getServerCertificateChain();
			if (l == null)
			{
				return null;
			}
			else
			{
				return (java.security.cert.Certificate[]) l.toArray();
			}
		}

		if (http == null)
		{
			throw new IllegalStateException("connection not yet open");
		}
		else
		{
			return (((HttpsClient) http).getServerCertificates());
		}
	}

	/**
	 * Returns the server's X.509 certificate chain, or null if the server did
	 * not authenticate.
	 */
	public javax.security.cert.X509Certificate[] getServerCertificateChain()
			throws SSLPeerUnverifiedException
	{
		if (cachedResponse != null)
		{
			throw new UnsupportedOperationException(
					"this method is not supported when using cache");
		}
		if (http == null)
		{
			throw new IllegalStateException("connection not yet open");
		}
		else
		{
			return ((HttpsClient) http).getServerCertificateChain();
		}
	}

	/**
	 * Returns the server's principal, or throws SSLPeerUnverifiedException if
	 * the server did not authenticate.
	 */
	Principal getPeerPrincipal() throws SSLPeerUnverifiedException
	{
		if (cachedResponse != null)
		{
			return ((SecureCacheResponse) cachedResponse).getPeerPrincipal();
		}

		if (http == null)
		{
			throw new IllegalStateException("connection not yet open");
		}
		else
		{
			return (((HttpsClient) http).getPeerPrincipal());
		}
	}

	/**
	 * Returns the principal the client sent to the server, or null if the
	 * client did not authenticate.
	 */
	Principal getLocalPrincipal()
	{
		if (cachedResponse != null)
		{
			return ((SecureCacheResponse) cachedResponse).getLocalPrincipal();
		}

		if (http == null)
		{
			throw new IllegalStateException("connection not yet open");
		}
		else
		{
			return (((HttpsClient) http).getLocalPrincipal());
		}
	}

}
