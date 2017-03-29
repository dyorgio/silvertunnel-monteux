/**
 * OnionCoffee - Anonymous Communication through TOR Network
 * Copyright (C) 2005-2007 RWTH Aachen University, Informatik IV
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */
/*
 * silvertunnel-ng.org Netlib - Java library to easily access anonymity networks
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

package cf.monteux.silvertunnel.netlib.layer.tor.circuit.cells;

import cf.monteux.silvertunnel.netlib.layer.tor.circuit.Circuit;
import cf.monteux.silvertunnel.netlib.layer.tor.circuit.Stream;

/**
 * 
 * @author Lexi Pimenidis
 * @author Tobias Boese
 */
public class CellRelaySendme extends CellRelay
{
	/**
	 * stream-level sendme cell.
	 * @param stream the stream to send the SENDME on
	 */
	public CellRelaySendme(final Stream stream)
	{
		// initialize a new Relay-cell
		super(stream, RELAY_SENDME);
	}

	/**
	 * circuit-level SENDME.
	 * 
	 * @param circuit
	 *            the circuit to send the SENDME on
	 * @param router
	 *            the router in the row to be addressed (starts with 0, ends
	 *            with c.routeEstablished - 1)
	 */
	public CellRelaySendme(final Circuit circuit, final int router)
	{
		// initialize a new Relay-cell
		super(circuit, RELAY_SENDME);
		setAddressedRouter(router);
	}
}
